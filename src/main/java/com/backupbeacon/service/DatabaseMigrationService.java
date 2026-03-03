package com.backupbeacon.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseMigrationService {
    private final JdbcTemplate jdbc;

    public DatabaseMigrationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void migrate() {
        ensureConnColumn("description", "ALTER TABLE db_connection ADD COLUMN description TEXT NOT NULL DEFAULT ''");
        ensureConnColumn("db_name", "ALTER TABLE db_connection ADD COLUMN db_name TEXT NOT NULL DEFAULT ''");
        ensureConnColumn("interval_minutes", "ALTER TABLE db_connection ADD COLUMN interval_minutes INTEGER NOT NULL DEFAULT 60");
        ensureConnColumn("backup_path", "ALTER TABLE db_connection ADD COLUMN backup_path TEXT NOT NULL DEFAULT '/backup-target'");
        ensureConnColumn("enabled", "ALTER TABLE db_connection ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1");
        ensureConnColumn("last_run_at", "ALTER TABLE db_connection ADD COLUMN last_run_at TEXT");

        ensureJobColumn("connection_id", "ALTER TABLE backup_job ADD COLUMN connection_id INTEGER");
        ensureJobColumn("handled", "ALTER TABLE backup_job ADD COLUMN handled INTEGER NOT NULL DEFAULT 0");
        jdbc.execute("UPDATE backup_job SET connection_id = -1 WHERE connection_id IS NULL");
        jdbc.execute("UPDATE backup_job SET handled = 0 WHERE handled IS NULL");

        ensureNoticeColumn("handled", "ALTER TABLE app_notice ADD COLUMN handled INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureConnColumn(String name, String ddl) {
        if (!columnExists("db_connection", name)) {
            jdbc.execute(ddl);
        }
    }

    private void ensureJobColumn(String name, String ddl) {
        if (!columnExists("backup_job", name)) {
            jdbc.execute(ddl);
        }
    }

    private void ensureNoticeColumn(String name, String ddl) {
        if (!columnExists("app_notice", name)) {
            jdbc.execute(ddl);
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