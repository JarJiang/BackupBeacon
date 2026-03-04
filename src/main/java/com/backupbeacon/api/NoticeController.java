package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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

    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "state", required = false, defaultValue = "pending") String state,
            @RequestParam(value = "level", required = false, defaultValue = "all") String level,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword
    ) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 10 : Math.min(size, 200);
        int offset = (safePage - 1) * safeSize;

        String fromClause = " FROM app_notice n ";
        List<String> whereParts = new ArrayList<String>();
        List<Object> params = new ArrayList<Object>();

        String normalizedState = state == null ? "pending" : state.trim().toLowerCase();
        if ("pending".equals(normalizedState)) {
            whereParts.add("n.handled = 0");
        } else if ("handled".equals(normalizedState)) {
            whereParts.add("n.handled = 1");
        }

        String normalizedLevel = level == null ? "all" : level.trim().toUpperCase();
        if (!"ALL".equals(normalizedLevel) && !normalizedLevel.isEmpty()) {
            whereParts.add("UPPER(COALESCE(n.level, '')) = ?");
            params.add(normalizedLevel);
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        if (!normalizedKeyword.isEmpty()) {
            String like = "%" + normalizedKeyword + "%";
            whereParts.add("(LOWER(COALESCE(n.content, '')) LIKE ? OR LOWER(COALESCE(n.level, '')) LIKE ? OR CAST(n.id AS TEXT) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String whereClause = whereParts.isEmpty() ? "" : (" WHERE " + String.join(" AND ", whereParts));

        String countSql = "SELECT COUNT(1)" + fromClause + whereClause;
        Integer totalValue = jdbc.queryForObject(countSql, Integer.class, params.toArray());
        int total = totalValue == null ? 0 : totalValue.intValue();

        String dataSql =
                "SELECT n.*" +
                        fromClause +
                        whereClause +
                        " ORDER BY n.id DESC LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<Object>(params);
        dataParams.add(safeSize);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
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