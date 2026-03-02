package com.backupbeacon.api;

import com.backupbeacon.service.BackupService;
import com.backupbeacon.service.CryptoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/connections")
public class ConnectionController {
    private final JdbcTemplate jdbc;
    private final CryptoService cryptoService;
    private final BackupService backupService;

    public ConnectionController(JdbcTemplate jdbc, CryptoService cryptoService, BackupService backupService) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
        this.backupService = backupService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT id, name, db_type, host, port, username, db_name, interval_minutes, backup_path, enabled, last_run_at, created_at FROM db_connection ORDER BY id DESC");
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String encryptedPassword = cryptoService.encrypt(String.valueOf(body.get("password")));
        jdbc.update(
                "INSERT INTO db_connection(name, db_type, host, port, username, password, db_name, interval_minutes, backup_path, enabled, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                body.get("name"), body.get("dbType"), body.get("host"), body.get("port"),
                body.get("username"), encryptedPassword, body.get("dbName"), body.get("intervalMinutes"),
                body.get("backupPath"), 1, Instant.now().toString()
        );
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @PostMapping("/{id}/run")
    public Map<String, Object> runNow(@PathVariable long id) {
        backupService.runConnection(id);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @PostMapping("/{id}/stop")
    public Map<String, Object> stopConnection(@PathVariable long id) {
        int affected = jdbc.update("UPDATE db_connection SET enabled=0 WHERE id=?", id);
        return Collections.<String, Object>singletonMap("affected", affected);
    }

    @PostMapping("/{id}/resume")
    public Map<String, Object> resumeConnection(@PathVariable long id) {
        int affected = jdbc.update("UPDATE db_connection SET enabled=1 WHERE id=?", id);
        return Collections.<String, Object>singletonMap("affected", affected);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteConnection(@PathVariable long id) {
        jdbc.update("DELETE FROM backup_job WHERE connection_id=?", id);
        int affected = jdbc.update("DELETE FROM db_connection WHERE id=?", id);
        return Collections.<String, Object>singletonMap("affected", affected);
    }
}