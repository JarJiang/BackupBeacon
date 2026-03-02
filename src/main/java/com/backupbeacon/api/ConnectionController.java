package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ConnectionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT * FROM db_connection ORDER BY id DESC");
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        jdbc.update(
                "INSERT INTO db_connection(name, db_type, host, port, username, password, created_at) VALUES(?,?,?,?,?,?,?)",
                body.get("name"), body.get("dbType"), body.get("host"), body.get("port"),
                body.get("username"), body.get("password"), Instant.now().toString()
        );
        return Collections.<String, Object>singletonMap("ok", true);
    }
}