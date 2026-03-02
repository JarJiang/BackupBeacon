package com.backupbeacon.api;

import com.backupbeacon.common.ApiException;
import com.backupbeacon.service.BackupService;
import com.backupbeacon.service.CryptoService;
import com.backupbeacon.service.FileSystemService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final FileSystemService fileSystemService;

    public ConnectionController(JdbcTemplate jdbc,
                                CryptoService cryptoService,
                                BackupService backupService,
                                FileSystemService fileSystemService) {
        this.jdbc = jdbc;
        this.cryptoService = cryptoService;
        this.backupService = backupService;
        this.fileSystemService = fileSystemService;
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

        String backupPath = String.valueOf(body.get("backupPath"));
        try {
            fileSystemService.ensureWritable(backupPath);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        String encryptedPassword = cryptoService.encrypt(String.valueOf(body.get("password")));
        jdbc.update(
                "INSERT INTO db_connection(name, db_type, host, port, username, password, db_name, interval_minutes, backup_path, enabled, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                body.get("name"), body.get("dbType"), body.get("host"), body.get("port"),
                body.get("username"), encryptedPassword, body.get("dbName"), body.get("intervalMinutes"),
                backupPath, 1, Instant.now().toString()
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "连接信息不完整，无法校验");
        }

        int port;
        try {
            port = Integer.parseInt(String.valueOf(portObj));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "端口格式不正确");
        }

        String jdbcUrl;
        if ("mysql".equalsIgnoreCase(dbType)) {
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000";
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 MySQL JDBC 驱动");
            }
        } else if ("postgres".equalsIgnoreCase(dbType) || "postgresql".equalsIgnoreCase(dbType)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?connectTimeout=5";
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 PostgreSQL JDBC 驱动");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "暂不支持该数据库类型");
        }

        try (Connection ignored = DriverManager.getConnection(jdbcUrl, username, password)) {
            // 连接成功即通过
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, buildFriendlyConnectionError(e));
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
            return "主机地址无法解析，请检查主机填写。本地运行建议使用 127.0.0.1";
        }
        if (lower.contains("connection refused") || lower.contains("communications link failure")) {
            return "无法连接到数据库，请检查主机和端口是否正确";
        }
        if (lower.contains("access denied") || lower.contains("password authentication failed")) {
            return "账号或密码错误，请检查登录凭据";
        }
        if (lower.contains("unknown database") || lower.contains("does not exist")) {
            return "数据库不存在，请检查库名是否正确";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "数据库连接超时，请检查网络连通性或防火墙规则";
        }
        return "数据库连接失败: " + raw;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }
}