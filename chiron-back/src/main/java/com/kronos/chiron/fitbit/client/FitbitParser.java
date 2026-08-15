package com.kronos.chiron.fitbit.client;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public final class FitbitParser {

    private FitbitParser() {
    }

    public static Map<LocalDate, Integer> stepsByDate(JsonNode rollUpResponse) {
        Map<LocalDate, Integer> result = new HashMap<>();
        if (rollUpResponse == null) return result;
        JsonNode points = rollUpResponse.get("rollupDataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            LocalDate date = googleDate(pt.get("civilStartTime"));
            if (date == null) continue;
            JsonNode steps = pt.get("steps");
            Long count = longField(steps, "count", "stepsSum", "steps_sum", "sum", "value");
            if (count == null) count = firstLong(steps);
            if (count != null) {
                result.put(date, count.intValue());
            }
        }
        return result;
    }

    public static Map<LocalDate, Double> sleepHoursByDate(JsonNode listResponse) {
        Map<LocalDate, Long> minutesByDate = new HashMap<>();
        if (listResponse != null) {
            JsonNode points = listResponse.get("dataPoints");
            if (points != null && points.isArray()) {
                for (JsonNode pt : points) {
                    JsonNode sleep = pt.get("sleep");
                    if (sleep == null) continue;
                    LocalDate date = sleepDate(sleep);
                    Long minutes = sleepMinutes(sleep);
                    if (date != null && minutes != null && minutes > 0) {
                        minutesByDate.merge(date, minutes, Long::sum);
                    }
                }
            }
        }
        Map<LocalDate, Double> hours = new HashMap<>();
        minutesByDate.forEach((d, m) -> hours.put(d, Math.round(m / 60.0 * 10.0) / 10.0));
        return hours;
    }

    public static Map<LocalDate, Integer> restingHeartRateByDate(JsonNode listResponse) {
        Map<LocalDate, Integer> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode hr = pt.get("dailyRestingHeartRate");
            if (hr == null) continue;
            Long bpm = longField(hr, "beatsPerMinute", "bpm", "value");
            if (bpm == null) bpm = firstLong(hr);
            LocalDate date = dataPointDate(pt, hr);
            if (bpm != null && date != null) {
                result.put(date, bpm.intValue());
            }
        }
        return result;
    }

    static LocalDate googleDate(JsonNode dateNode) {
        if (dateNode == null || !dateNode.hasNonNull("year")
                || !dateNode.hasNonNull("month") || !dateNode.hasNonNull("day")) {
            return null;
        }
        try {
            return LocalDate.of(dateNode.get("year").asInt(),
                    dateNode.get("month").asInt(), dateNode.get("day").asInt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate sleepDate(JsonNode sleep) {
        JsonNode interval = sleep.get("interval");
        if (interval == null) return null;
        for (String field : new String[]{"civilEndTime", "endTime", "civilStartTime", "startTime"}) {
            LocalDate d = parseDatePrefix(interval.get(field));
            if (d != null) return d;
        }
        LocalDate civil = googleDate(interval.get("civilEndTime"));
        return civil != null ? civil : googleDate(interval.get("civilStartTime"));
    }

    private static Long sleepMinutes(JsonNode sleep) {
        Long fromSummary = longField(sleep.get("summary"), "minutesAsleep");
        return fromSummary != null ? fromSummary : longField(sleep, "minutesAsleep");
    }

    private static LocalDate dataPointDate(JsonNode dataPoint, JsonNode value) {
        LocalDate d = googleDate(value.get("date"));
        if (d != null) return d;
        d = googleDate(dataPoint.get("date"));
        if (d != null) return d;
        d = parseDatePrefix(value.get("date"));
        return d != null ? d : parseDatePrefix(dataPoint.get("date"));
    }

    private static LocalDate parseDatePrefix(JsonNode node) {
        if (node == null) return null;
        String s = node.asText();
        if (s == null || s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Long longField(JsonNode obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            JsonNode f = obj.get(n);
            if (f == null || f.isNull() || f.isObject() || f.isArray()) continue;
            if (f.isNumber()) return f.asLong();
            try {
                return Long.parseLong(f.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    static Long firstLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asLong();
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                Long v = firstLong(child);
                if (v != null) return v;
            }
            return null;
        }
        try {
            return Long.parseLong(node.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
