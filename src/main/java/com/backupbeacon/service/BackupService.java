package com.backupbeacon.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
            runConnection(((Number) conn.get("id")).longValue(), false);
        }
    }

    public void runConnectionNow(long connectionId) {
        runConnection(connectionId, true);
    }

    private synchronized void runConnection(long connectionId, boolean force) {
        Map<String, Object> conn = jdbc.queryForMap("SELECT * FROM db_connection WHERE id=?", connectionId);

        if (!force && !isDue(conn)) {
            return;
        }

        String started = Instant.now().toString();
        boolean hasPolicyId = columnExists("backup_job", "policy_id");
        if (hasPolicyId) {
            jdbc.update("INSERT INTO backup_job(policy_id, connection_id, status, message, started_at) VALUES(?,?,?,?,?)",
                    connectionId, connectionId, "RUNNING", "job started", started);
        } else {
            jdbc.update("INSERT INTO backup_job(connection_id, status, message, started_at) VALUES(?,?,?,?)",
                    connectionId, "RUNNING", "job started", started);
        }

        Long jobIdObj = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long jobId = jobIdObj == null ? 0L : jobIdObj;

        File tempOut = null;
        try {
            String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String timePart = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            String connName = String.valueOf(conn.get("name"));
            String dbName = String.valueOf(conn.get("db_name"));

            File baseDir = resolveBackupRoot(String.valueOf(conn.get("backup_path")));
            File dir = new File(baseDir, connName + File.separator + dateDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("无法创建备份目录: " + dir.getAbsolutePath() + "。本地Windows建议使用 ./backup-target 或 D:/backup-target");
            }

            File out = new File(dir, dbName + "-" + timePart + ".sql");
            tempOut = new File(dir, dbName + "-" + timePart + ".tmp.sql");
            if (tempOut.exists()) {
                tempOut.delete();
            }

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
                throw new RuntimeException("暂不支持该数据库类型: " + dbType);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dbType.startsWith("postgres")) {
                pb.environment().put("PGPASSWORD", rawPassword);
            }
            pb.redirectOutput(tempOut);
            pb.redirectErrorStream(true);

            int code;
            try {
                code = pb.start().waitFor();
            } catch (IOException e) {
                throw new RuntimeException(buildCommandNotFoundMessage(dbType, e));
            }
            if (code != 0) {
                throw new RuntimeException("备份命令执行失败，退出码=" + code);
            }

            Files.move(tempOut.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='SUCCESS', message=?, file_path=?, ended_at=? WHERE id=?",
                    "backup success", out.getAbsolutePath(), ended, jobId);
            jdbc.update("UPDATE db_connection SET last_run_at=? WHERE id=?", ended, connectionId);
        } catch (Exception e) {
            if (tempOut != null && tempOut.exists()) {
                tempOut.delete();
            }
            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='FAILED', message=?, ended_at=? WHERE id=?", e.getMessage(), ended, jobId);
            jdbc.update("UPDATE db_connection SET last_run_at=? WHERE id=?", ended, connectionId);
            jdbc.update("INSERT INTO app_notice(level, content, created_at, handled) VALUES(?,?,?,0)",
                    "ERROR", "connection " + connectionId + " failed: " + e.getMessage(), ended);
        }
    }

    private boolean isDue(Map<String, Object> conn) {
        Object enabled = conn.get("enabled");
        if (!(enabled instanceof Number) || ((Number) enabled).intValue() != 1) {
            return false;
        }

        String lastRunAt = conn.get("last_run_at") == null ? null : String.valueOf(conn.get("last_run_at"));
        if (lastRunAt == null || lastRunAt.trim().isEmpty()) {
            return true;
        }

        int interval = 60;
        if (conn.get("interval_minutes") instanceof Number) {
            interval = ((Number) conn.get("interval_minutes")).intValue();
        }

        Integer due = jdbc.queryForObject(
                "SELECT CASE WHEN datetime(?, '+' || ? || ' minutes') <= datetime('now') THEN 1 ELSE 0 END",
                Integer.class,
                lastRunAt,
                interval
        );
        return due != null && due.intValue() == 1;
    }

    private String buildCommandNotFoundMessage(String dbType, IOException e) {
        String raw = e == null ? "" : String.valueOf(e.getMessage());
        String lower = raw.toLowerCase();
        if (lower.contains("createprocess error=2") || lower.contains("cannot run program")) {
            if ("mysql".equals(dbType)) {
                return "未找到 mysqldump 命令。请安装 MySQL Client 并将 mysqldump 加入系统 PATH";
            }
            if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
                return "未找到 pg_dump 命令。请安装 PostgreSQL Client 并将 pg_dump 加入系统 PATH";
            }
        }
        return "启动备份命令失败: " + raw;
    }

    private File resolveBackupRoot(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) {
            path = "./backup-target";
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows && path.startsWith("/") && !path.startsWith("//")) {
            path = "." + path.replace("/", File.separator);
        }
        return new File(path);
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