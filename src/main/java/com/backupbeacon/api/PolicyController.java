package com.backupbeacon.api;

import com.backupbeacon.service.BackupService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private final JdbcTemplate jdbc;
    private final BackupService backupService;

    public PolicyController(JdbcTemplate jdbc, BackupService backupService) {
        this.jdbc = jdbc;
        this.backupService = backupService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT p.*, c.name AS connection_name FROM backup_policy p JOIN db_connection c ON p.connection_id=c.id ORDER BY p.id DESC");
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        jdbc.update(
                "INSERT INTO backup_policy(name, connection_id, database_name, tables_csv, mode, interval_minutes, backup_path, enabled, created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                body.get("name"), body.get("connectionId"), body.get("databaseName"), body.getOrDefault("tablesCsv", ""),
                body.get("mode"), body.get("intervalMinutes"), body.get("backupPath"), body.getOrDefault("enabled", 1), Instant.now().toString()
        );
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @PostMapping("/{id}/run")
    public Map<String, Object> runNow(@PathVariable long id) {
        backupService.runPolicy(id);
        return Collections.<String, Object>singletonMap("ok", true);
    }
}