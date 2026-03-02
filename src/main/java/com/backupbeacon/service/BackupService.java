package com.backupbeacon.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@EnableScheduling
public class BackupService {
    private final JdbcTemplate jdbc;

    public BackupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledRun() {
        List<Map<String, Object>> due = jdbc.queryForList(
                "SELECT * FROM backup_policy WHERE enabled=1 AND (last_run_at IS NULL OR datetime(last_run_at, '+' || interval_minutes || ' minutes') <= datetime('now'))"
        );
        for (Map<String, Object> policy : due) {
            runPolicy(((Number) policy.get("id")).longValue());
        }
    }

    public synchronized void runPolicy(long policyId) {
        Map<String, Object> policy = jdbc.queryForMap("SELECT * FROM backup_policy WHERE id=?", policyId);
        Map<String, Object> conn = jdbc.queryForMap("SELECT * FROM db_connection WHERE id=?", policy.get("connection_id"));

        String started = Instant.now().toString();
        jdbc.update("INSERT INTO backup_job(policy_id, status, message, started_at) VALUES(?,?,?,?)", policyId, "RUNNING", "job started", started);
        Long jobIdObj = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long jobId = jobIdObj == null ? 0L : jobIdObj;

        try {
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now());
            String fileName = policy.get("database_name") + "_" + ts + ".sql";
            File dir = new File(String.valueOf(policy.get("backup_path")));
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("cannot create backup directory: " + dir.getAbsolutePath());
            }
            File out = new File(dir, fileName);

            List<String> cmd = new ArrayList<String>();
            String dbType = String.valueOf(conn.get("db_type")).toLowerCase();
            if ("mysql".equals(dbType)) {
                cmd.add("mysqldump");
                cmd.add("-h"); cmd.add(String.valueOf(conn.get("host")));
                cmd.add("-P"); cmd.add(String.valueOf(conn.get("port")));
                cmd.add("-u"); cmd.add(String.valueOf(conn.get("username")));
                cmd.add("-p" + String.valueOf(conn.get("password")));
                cmd.add(String.valueOf(policy.get("database_name")));

                String tables = String.valueOf(policy.get("tables_csv"));
                if (tables != null && !tables.trim().isEmpty()) {
                    String[] arr = tables.split(",");
                    for (String t : arr) {
                        cmd.add(t.trim());
                    }
                }
            } else if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
                cmd.add("pg_dump");
                cmd.add("-h"); cmd.add(String.valueOf(conn.get("host")));
                cmd.add("-p"); cmd.add(String.valueOf(conn.get("port")));
                cmd.add("-U"); cmd.add(String.valueOf(conn.get("username")));
                cmd.add("-d"); cmd.add(String.valueOf(policy.get("database_name")));
            } else {
                throw new RuntimeException("unsupported db type: " + dbType);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dbType.startsWith("postgres")) {
                pb.environment().put("PGPASSWORD", String.valueOf(conn.get("password")));
            }
            pb.redirectOutput(out);
            pb.redirectErrorStream(true);

            int code = pb.start().waitFor();
            if (code != 0) {
                throw new RuntimeException("backup command failed, exit code=" + code);
            }

            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='SUCCESS', message=?, file_path=?, ended_at=? WHERE id=?", "backup success", out.getAbsolutePath(), ended, jobId);
            jdbc.update("UPDATE backup_policy SET last_run_at=? WHERE id=?", ended, policyId);
        } catch (Exception e) {
            String ended = Instant.now().toString();
            jdbc.update("UPDATE backup_job SET status='FAILED', message=?, ended_at=? WHERE id=?", e.getMessage(), ended, jobId);
            jdbc.update("INSERT INTO app_notice(level, content, created_at, handled) VALUES(?,?,?,0)", "ERROR", "policy " + policyId + " failed: " + e.getMessage(), ended);
        }
    }
}