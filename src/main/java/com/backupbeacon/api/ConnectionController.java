package com.backupbeacon.api;

import com.backupbeacon.service.CryptoService;
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
    private final CryptoService cryptoService;

    public ConnectionController(JdbcTemplate jdbc, CryptoService cryptoService) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        // 不向前端返回密码字段，避免敏感信息泄露。
        return jdbc.queryForList("SELECT id, name, db_type, host, port, username, db_name, created_at FROM db_connection ORDER BY id DESC");
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String encryptedPassword = cryptoService.encrypt(String.valueOf(body.get("password")));
        jdbc.update(
                "INSERT INTO db_connection(name, db_type, host, port, username, password, db_name, created_at) VALUES(?,?,?,?,?,?,?,?)",
                body.get("name"), body.get("dbType"), body.get("host"), body.get("port"),
                body.get("username"), encryptedPassword, body.get("dbName"), Instant.now().toString()
        );
        return Collections.<String, Object>singletonMap("ok", true);
    }
}