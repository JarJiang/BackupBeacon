package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
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
    public List<Map<String, Object>> list(@RequestParam(value = "history", required = false, defaultValue = "false") boolean history) {
        if (history) {
            return jdbc.queryForList("SELECT j.*, c.name AS connection_name FROM backup_job j LEFT JOIN db_connection c ON j.connection_id=c.id ORDER BY j.id DESC LIMIT 1000");
        }

        return jdbc.queryForList(
                "SELECT j.*, c.name AS connection_name " +
                        "FROM backup_job j " +
                        "LEFT JOIN db_connection c ON j.connection_id=c.id " +
                        "WHERE j.handled=0 " +
                        "AND datetime(replace(replace(COALESCE(j.ended_at, j.started_at), 'T', ' '), 'Z', '')) >= datetime('now', '-1 day') " +
                        "ORDER BY j.id DESC LIMIT 200"
        );
    }

    @GetMapping("/unread-failed-count")
    public Map<String, Object> unreadFailedCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM backup_job WHERE handled=0 AND UPPER(status)='FAILED'",
                Integer.class
        );
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("count", count == null ? 0 : count.intValue());
        return result;
    }

    @PostMapping("/mark-all-read")
    public Map<String, Object> markAllRead() {
        int affected = jdbc.update("UPDATE backup_job SET handled=1 WHERE handled=0");
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        result.put("affected", affected);
        return result;
    }
}