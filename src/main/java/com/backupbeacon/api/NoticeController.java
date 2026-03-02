package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final JdbcTemplate jdbc;

    public NoticeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT * FROM app_notice ORDER BY id DESC LIMIT 100");
    }

    @GetMapping("/unread-error-count")
    public Map<String, Object> unreadErrorCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_notice WHERE handled=0 AND UPPER(level)='ERROR'",
                Integer.class
        );
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("count", count == null ? 0 : count.intValue());
        return result;
    }

    @PostMapping("/{id}/handle")
    public Map<String, Object> handle(@PathVariable long id) {
        jdbc.update("UPDATE app_notice SET handled=1 WHERE id=?", id);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        return result;
    }

    @PostMapping("/mark-all-read")
    public Map<String, Object> markAllRead() {
        int affected = jdbc.update("UPDATE app_notice SET handled=1 WHERE handled=0");
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("affected", affected);
        return result;
    }
}