package com.backupbeacon.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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

    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(value = "history", required = false, defaultValue = "false") boolean history,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "status", required = false, defaultValue = "all") String status,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "timeRange", required = false, defaultValue = "all") String timeRange
    ) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 10 : Math.min(size, 200);
        int offset = (safePage - 1) * safeSize;

        String fromClause = " FROM backup_job j LEFT JOIN db_connection c ON j.connection_id = c.id ";
        String timeExpr = "datetime(replace(replace(COALESCE(j.ended_at, j.started_at), 'T', ' '), 'Z', ''))";

        List<String> whereParts = new ArrayList<String>();
        List<Object> params = new ArrayList<Object>();

        if (!history) {
            whereParts.add("j.handled = 0");
            whereParts.add(timeExpr + " >= datetime('now', '-1 day')");
        }

        String normalizedStatus = status == null ? "all" : status.trim().toUpperCase();
        if (!"ALL".equals(normalizedStatus) && !normalizedStatus.isEmpty()) {
            whereParts.add("UPPER(COALESCE(j.status, '')) = ?");
            params.add(normalizedStatus);
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        if (!normalizedKeyword.isEmpty()) {
            String like = "%" + normalizedKeyword + "%";
            whereParts.add("(LOWER(COALESCE(c.name, '')) LIKE ? OR LOWER(COALESCE(j.message, '')) LIKE ? OR LOWER(COALESCE(j.file_path, '')) LIKE ? OR CAST(j.id AS TEXT) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String modifier = resolveTimeModifier(timeRange);
        if (modifier != null) {
            whereParts.add(timeExpr + " >= datetime('now', ?)");
            params.add(modifier);
        }

        String whereClause = whereParts.isEmpty() ? "" : (" WHERE " + String.join(" AND ", whereParts));

        String countSql = "SELECT COUNT(1)" + fromClause + whereClause;
        Integer totalValue = jdbc.queryForObject(countSql, Integer.class, params.toArray());
        int total = totalValue == null ? 0 : totalValue.intValue();

        String dataSql =
                "SELECT j.*, c.name AS connection_name" +
                        fromClause +
                        whereClause +
                        " ORDER BY j.id DESC LIMIT ? OFFSET ?";

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

    private String resolveTimeModifier(String timeRange) {
        if (timeRange == null) {
            return null;
        }
        String value = timeRange.trim().toLowerCase();
        if ("24h".equals(value)) {
            return "-1 day";
        }
        if ("7d".equals(value)) {
            return "-7 day";
        }
        if ("30d".equals(value)) {
            return "-30 day";
        }
        return null;
    }
}