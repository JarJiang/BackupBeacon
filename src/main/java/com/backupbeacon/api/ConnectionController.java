package com.backupbeacon.api;

import com.backupbeacon.service.BackupService;
import com.backupbeacon.service.CryptoService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
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

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> body) {
        validateAndTestConnection(body);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        validateAndTestConnection(body);

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

    private void validateAndTestConnection(Map<String, Object> body) {
        String dbType = String.valueOf(body.get("dbType"));
        String host = String.valueOf(body.get("host"));
        String username = String.valueOf(body.get("username"));
        String password = String.valueOf(body.get("password"));
        String dbName = String.valueOf(body.get("dbName"));

        Object portObj = body.get("port");
        if (isBlank(dbType) || isBlank(host) || isBlank(username) || isBlank(password) || isBlank(dbName) || portObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "连接信息不完整，无法校验");
        }

        int port;
        try {
            port = Integer.parseInt(String.valueOf(portObj));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "端口格式不正确");
        }

        String jdbcUrl;
        if ("mysql".equalsIgnoreCase(dbType)) {
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000";
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 MySQL JDBC 驱动");
            }
        } else if ("postgres".equalsIgnoreCase(dbType) || "postgresql".equalsIgnoreCase(dbType)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?connectTimeout=5";
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 PostgreSQL JDBC 驱动");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该数据库类型");
        }

        try (Connection ignored = DriverManager.getConnection(jdbcUrl, username, password)) {
            // 连接成功即通过
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, buildFriendlyConnectionError(e));
        }
    }


    private String buildFriendlyConnectionError(Exception e) {
        String raw = e == null ? "" : String.valueOf(e.getMessage());
        String lower = raw.toLowerCase();

        Throwable root = e;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }

        if (root instanceof java.net.UnknownHostException) {
            return "\u4e3b\u673a\u5730\u5740\u65e0\u6cd5\u89e3\u6790\uff0c\u8bf7\u68c0\u67e5\u4e3b\u673a\u586b\u5199\u3002\u672c\u5730\u8fd0\u884c\u5efa\u8bae\u4f7f\u7528 127.0.0.1";
        }
        if (lower.contains("connection refused") || lower.contains("communications link failure")) {
            return "\u65e0\u6cd5\u8fde\u63a5\u5230\u6570\u636e\u5e93\uff0c\u8bf7\u68c0\u67e5\u4e3b\u673a\u548c\u7aef\u53e3\u662f\u5426\u6b63\u786e";
        }
        if (lower.contains("access denied") || lower.contains("password authentication failed")) {
            return "\u8d26\u53f7\u6216\u5bc6\u7801\u9519\u8bef\uff0c\u8bf7\u68c0\u67e5\u767b\u5f55\u51ed\u636e";
        }
        if (lower.contains("unknown database") || lower.contains("does not exist")) {
            return "\u6570\u636e\u5e93\u4e0d\u5b58\u5728\uff0c\u8bf7\u68c0\u67e5\u5e93\u540d\u662f\u5426\u6b63\u786e";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "\u6570\u636e\u5e93\u8fde\u63a5\u8d85\u65f6\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u901a\u6027\u6216\u9632\u706b\u5899\u89c4\u5219";
        }
        return "\u6570\u636e\u5e93\u8fde\u63a5\u5931\u8d25: " + raw;
    }
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }
}