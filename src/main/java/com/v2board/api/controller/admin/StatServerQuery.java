package com.v2board.api.controller.admin;

import com.v2board.api.common.BusinessException;
import com.v2board.api.model.StatServer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Date-range query helpers for {@code v2_stat_server}. Days are UTC, matching {@code recordStatServer}.
 */
final class StatServerQuery {

    static final int MAX_RANGE_DAYS = 62;

    private StatServerQuery() {
    }

    static LocalDate parseUtcDate(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("请选择" + fieldLabel);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(fieldLabel + "格式不正确");
        }
    }

    static void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException("查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    static List<String> typesForQuery(String serverType) {
        if ("vmess".equals(serverType) || "v2ray".equals(serverType)) {
            return List.of("vmess", "v2ray");
        }
        return List.of(serverType);
    }

    static long utcStartEpoch(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    static String formatUtcDate(long recordAt) {
        return Instant.ofEpochSecond(recordAt).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    static Map<String, Object> assemble(
            Long serverId,
            String serverType,
            String serverName,
            LocalDate start,
            LocalDate end,
            List<StatServer> rows) {
        // Merge same UTC day (e.g. vmess + legacy v2ray rows).
        Map<Long, long[]> byDay = new TreeMap<>(Comparator.reverseOrder());
        if (rows != null) {
            for (StatServer row : rows) {
                if (row == null || row.getRecordAt() == null) {
                    continue;
                }
                long u = row.getU() != null ? row.getU() : 0L;
                long d = row.getD() != null ? row.getD() : 0L;
                long[] bytes = byDay.computeIfAbsent(row.getRecordAt(), k -> new long[2]);
                bytes[0] += u;
                bytes[1] += d;
            }
        }

        long totalU = 0L;
        long totalD = 0L;
        List<Map<String, Object>> days = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : byDay.entrySet()) {
            long u = e.getValue()[0];
            long d = e.getValue()[1];
            totalU += u;
            totalD += d;
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("record_at", e.getKey());
            day.put("date", formatUtcDate(e.getKey()));
            day.put("u", u);
            day.put("d", d);
            day.put("total", u + d);
            days.add(day);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("server_id", serverId);
        data.put("server_type", serverType);
        data.put("server_name", serverName != null ? serverName : "");
        data.put("start_date", start.toString());
        data.put("end_date", end.toString());
        data.put("u", totalU);
        data.put("d", totalD);
        data.put("total", totalU + totalD);
        data.put("days", days);
        return data;
    }
}
