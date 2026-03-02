package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
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

    @PostMapping("/{id}/handle")
    public Map<String, Object> handle(@PathVariable long id) {
        jdbc.update("UPDATE app_notice SET handled=1 WHERE id=?", id);
        return Collections.<String, Object>singletonMap("ok", true);
    }
}