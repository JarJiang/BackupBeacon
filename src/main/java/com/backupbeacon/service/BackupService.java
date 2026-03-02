package com.backupbeacon.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@EnableScheduling
public class BackupService {
    private final JdbcTemplate jdbc;
    private final CryptoService cryptoService;

    public BackupService(JdbcTemplate jdbc, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledRun() {
        List<Map<String, Object>> due = jdbc.queryForList(
                "SELECT * FROM db_connection WHERE enabled=1 AND (last_run_at IS NULL OR datetime(last_run_at, '+' || interval_minutes || ' minutes') <= datetime('now'))"
        );
        for (Map<String, Object> conn : due) {
            runConnection(((Number) conn.get("id")).longValue());
        }
    }

    public synchronized void runConnection(long connectionId) {
        Map<String, Object> conn = jdbc.queryForMap("SELECT * FROM db_connection WHERE id=?", connectionId);

        String started = Instant.now().toString();
        boolean hasPolicyId = columnExists("backup_job", "policy_id");
        if (hasPolicyId) {
            // 兼容旧表结构：backup_job 仍要求 policy_id NOT NULL。
            jdbc.update("INSERT INTO backup_job(policy_id, connection_id, status, message, started_at) VALUES(?,?,?,?,?)", connectionId, connectionId, "RUNNING", "job started", started);
        } else {
            jdbc.update("INSERT INTO backup_job(connection_id, status, message, started_at) VALUES(?,?,?,?)", connectionId, "RUNNING", "job started", started);
        }

        Long jobIdObj = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long jobId = jobIdObj == null ? 0L : jobIdObj;

        try {
            String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String timePart = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            String connName = String.valueOf(conn.get("name"));
            String dbName = String.valueOf(conn.get("db_name"));

            File dir = new File(String.valueOf(conn.get("backup_path")), connName + File.separator + dateDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("cannot create backup directory: " + dir.getAbsolutePath());
            }
            File out = new File(dir, dbName + "-" + timePart + ".sql");

            String rawPassword = cryptoService.decryptIfNeeded(String.valueOf(conn.get("password")));
            String dbType = String.valueOf(conn.get("db_type")).toLowerCase();
            List<String> cmd = new ArrayList<String>();
            if ("mysql".equals(dbType)) {
                cmd.add("mysqldump");
                cmd.add("-h"); cmd.add(String.valueOf(conn.get("host")));
                cmd.add("-P"); cmd.add(String.valueOf(conn.get("port")));
                cmd.add("-u"); cmd.add(String.valueOf(conn.get("username")));
                cmd.add("-p" + rawPassword);
                cmd.add(dbName);
            } else if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
                cmd.add("pg_dump");
                cmd.add("-h"); cmd.add(String.valueOf(conn.get("host")));
                cmd.add("-p"); cmd.add(String.valueOf(conn.get("port")));
                cmd.add("-U"); cmd.add(String.valueOf(conn.get("username")));
                cmd.add("-d"); cmd.add(dbName);
            } else {
                throw new RuntimeException("unsupported db type: " + dbType);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dbType.startsWith("postgres")) {
                pb.environment().put("PGPASSWORD", rawPassword);
            }
            pb.redirectOutput(out);
            pb.redirectErrorStream(true);

            int code = pb.start().waitFor();
            if (code != 0) {
                throw new RuntimeException("backup command failed, exit code=" + code);
            }

            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='SUCCESS', message=?, file_path=?, ended_at=? WHERE id=?", "backup success", out.getAbsolutePath(), ended, jobId);
            jdbc.update("UPDATE db_connection SET last_run_at=? WHERE id=?", ended, connectionId);
        } catch (Exception e) {
            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='FAILED', message=?, ended_at=? WHERE id=?", e.getMessage(), ended, jobId);
            jdbc.update("INSERT INTO app_notice(level, content, created_at, handled) VALUES(?,?,?,0)", "ERROR", "connection " + connectionId + " failed: " + e.getMessage(), ended);
        }
    }

    private boolean columnExists(String table, String column) {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> col : columns) {
            if (column.equals(String.valueOf(col.get("name")))) {
                return true;
            }
        }
        return false;
    }
}