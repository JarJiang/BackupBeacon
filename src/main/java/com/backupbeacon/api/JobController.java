package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JdbcTemplate jdbc;

    public JobController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT j.*, p.name AS policy_name FROM backup_job j JOIN backup_policy p ON j.policy_id=p.id ORDER BY j.id DESC LIMIT 200");
    }
}