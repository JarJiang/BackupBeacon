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
        ensureDbNameColumn();
    }

    private void ensureDbNameColumn() {
        List<Map<String, Object>> columns = jdbc.queryForList("PRAGMA table_info(db_connection)");
        boolean exists = false;
        for (Map<String, Object> col : columns) {
            if ("db_name".equals(String.valueOf(col.get("name")))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            jdbc.execute("ALTER TABLE db_connection ADD COLUMN db_name TEXT NOT NULL DEFAULT ''");
        }
    }
}