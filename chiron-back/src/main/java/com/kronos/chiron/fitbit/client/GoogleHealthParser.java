package com.kronos.chiron.fitbit.client;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GoogleHealthParser {

    private GoogleHealthParser() {
    }

    public static String nextPageToken(JsonNode response) {
        if (response == null) return null;
        JsonNode token = response.get("nextPageToken");
        return token != null && token.isTextual() && !token.asText().isBlank() ? token.asText() : null;
    }

    public static Map<LocalDate, Double> dailyRollupByDate(JsonNode rollUpResponse, String valueNodeField,
            String... sumFieldCandidates) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (rollUpResponse == null) return result;
        JsonNode points = rollUpResponse.get("rollupDataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            LocalDate date = FitbitParser.googleDate(pt.get("civilStartTime"));
            if (date == null) continue;
            JsonNode value = pt.get(valueNodeField);
            Long sum = FitbitParser.longField(value, sumFieldCandidates);
            if (sum == null) sum = FitbitParser.firstLong(value);
            if (sum != null) {
                result.merge(date, sum.doubleValue(), Double::sum);
            }
        }
        return result;
    }

    public record ZoneMinutes(Integer bruleuse, Integer cardio, Integer pic) {
    }

    public static Map<LocalDate, ZoneMinutes> zoneMinutesByDate(JsonNode rollUpResponse) {
        Map<LocalDate, int[]> acc = new HashMap<>();
        if (rollUpResponse != null) {
            JsonNode points = rollUpResponse.get("rollupDataPoints");
            if (points != null && points.isArray()) {
                for (JsonNode pt : points) {
                    LocalDate date = FitbitParser.googleDate(pt.get("civilStartTime"));
                    if (date == null) continue;
                    JsonNode value = pt.get("timeInHeartRateZone");
                    if (value == null) continue;
                    Iterable<JsonNode> entries = value.isArray() ? value : List.of(value);
                    for (JsonNode entry : entries) {
                        JsonNode zoneNode = entry.get("heartRateZone");
                        if (zoneNode == null) zoneNode = entry.get("zoneType");
                        String zone = zoneNode != null ? zoneNode.asText(null) : null;
                        Long seconds = FitbitParser.longField(entry, "durationSum", "duration", "seconds");
                        if (zone == null || seconds == null) continue;
                        int minutes = (int) Math.round(seconds / 60.0);
                        int[] bucket = acc.computeIfAbsent(date, d -> new int[3]);
                        String z = zone.toUpperCase(java.util.Locale.ROOT);
                        if (z.contains("PEAK")) bucket[2] += minutes;
                        else if (z.contains("CARDIO")) bucket[1] += minutes;
                        else if (z.contains("FAT_BURN") || z.contains("FATBURN")) bucket[0] += minutes;
                    }
                }
            }
        }
        Map<LocalDate, ZoneMinutes> result = new HashMap<>();
        acc.forEach((d, b) -> result.put(d, new ZoneMinutes(b[0], b[1], b[2])));
        return result;
    }

    public record FcBucket(Instant debut, Integer min, Double moyenne, Integer max, Integer nbEchantillons) {
    }

    public static List<FcBucket> heartRateBuckets(JsonNode rollUpResponse) {
        List<FcBucket> result = new ArrayList<>();
        if (rollUpResponse == null) return result;
        JsonNode points = rollUpResponse.get("rollupDataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode startNode = pt.get("startTime");
            if (startNode == null || !startNode.isTextual()) continue;
            Instant debut;
            try {
                debut = Instant.parse(startNode.asText());
            } catch (RuntimeException e) {
                continue;
            }
            JsonNode hr = pt.get("heartRate");
            if (hr == null) continue;
            Long min = FitbitParser.longField(hr, "beatsPerMinuteMin", "min", "minBeatsPerMinute", "bpmMin");
            Long avg = FitbitParser.longField(hr, "beatsPerMinuteAvg", "beatsPerMinuteMean", "avg", "average",
                    "mean", "bpmAvg");
            Long max = FitbitParser.longField(hr, "beatsPerMinuteMax", "max", "maxBeatsPerMinute", "bpmMax");
            Long count = FitbitParser.longField(hr, "sampleCount", "count", "numSamples");
            if (avg == null) avg = FitbitParser.firstLong(hr);
            if (min == null && max == null && avg == null) continue;
            result.add(new FcBucket(debut, min != null ? min.intValue() : null,
                    avg != null ? avg.doubleValue() : null, max != null ? max.intValue() : null,
                    count != null ? count.intValue() : null));
        }
        return result;
    }

    public record VfcJour(LocalDate date, Double vfcMs, Double vfcSommeilProfondMs) {
    }

    public static Map<LocalDate, VfcJour> hrvByDate(JsonNode listResponse) {
        Map<LocalDate, VfcJour> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode hrv = pt.get("dailyHeartRateVariability");
            if (hrv == null) continue;
            LocalDate date = dataPointDate(pt, hrv);
            if (date == null) continue;
            Double avg = doubleField(hrv, "averageHeartRateVariabilityMilliseconds", "averageHrvMilliseconds",
                    "value");
            Double deepSleep = doubleField(hrv, "deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds",
                    "deepSleepRmssdMilliseconds");
            result.put(date, new VfcJour(date, avg, deepSleep));
        }
        return result;
    }

    public record Vo2MaxJour(LocalDate date, Double vo2Max, String niveauAptitude) {
    }

    public static Map<LocalDate, Vo2MaxJour> vo2MaxByDate(JsonNode listResponse) {
        Map<LocalDate, Vo2MaxJour> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode vo2 = pt.get("dailyVo2Max");
            if (vo2 == null) continue;
            LocalDate date = dataPointDate(pt, vo2);
            if (date == null) continue;
            Double value = doubleField(vo2, "vo2Max", "value");
            JsonNode niveauNode = vo2.get("cardioFitnessLevel");
            String niveau = niveauNode != null && niveauNode.isTextual() ? niveauNode.asText() : null;
            result.put(date, new Vo2MaxJour(date, value, niveau));
        }
        return result;
    }

    public static Map<LocalDate, Double> dailyDoubleByDate(JsonNode listResponse, String parentField,
            String... valueFieldCandidates) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode value = pt.get(parentField);
            if (value == null) continue;
            LocalDate date = dataPointDate(pt, value);
            if (date == null) continue;
            Double v = doubleField(value, valueFieldCandidates);
            if (v != null) result.put(date, v);
        }
        return result;
    }

    public record SommeilBrut(String externalId, Instant debut, Instant fin, LocalDate date, boolean sieste,
            boolean stadesDisponibles, Integer minutesEndormi, Integer minutesEveille,
            Integer minutesAvantEndormissement, Integer minutesApresReveil, Integer minutesProfond,
            Integer minutesLeger, Integer minutesParadoxal, Integer minutesAgite, Integer nbReveils) {
    }

    public static List<SommeilBrut> sleepSessions(JsonNode listResponse) {
        List<SommeilBrut> result = new ArrayList<>();
        if (listResponse == null) return result;
        JsonNode points = listResponse.get("dataPoints");
        if (points == null || !points.isArray()) return result;
        for (JsonNode pt : points) {
            JsonNode sleep = pt.get("sleep");
            if (sleep == null) continue;
            JsonNode interval = sleep.get("interval");
            Instant debut = instantField(interval, "startTime");
            Instant fin = instantField(interval, "endTime");
            if (debut == null && fin == null) continue;
            LocalDate date = fin != null ? asLocalDate(fin) : asLocalDate(debut);

            JsonNode metadata = sleep.get("metadata");
            String externalId = metadata != null && metadata.hasNonNull("externalId")
                    ? metadata.get("externalId").asText()
                    : null;
            boolean sieste = metadata != null && metadata.path("nap").asBoolean(false);
            boolean stadesDisponibles = metadata != null
                    && "SUCCEEDED".equals(metadata.path("stagesStatus").asText(null));

            JsonNode summary = sleep.get("summary");
            Integer minutesEndormi = intField(summary, "minutesAsleep");
            Integer minutesEveilleSummary = intField(summary, "minutesAwake");
            Integer minutesAvant = intField(summary, "minutesToFallAsleep");
            Integer minutesApres = intField(summary, "minutesAfterWakeUp");

            Map<String, Integer> parStade = new HashMap<>();
            Integer nbReveils = null;
            if (summary != null) {
                JsonNode stagesSummary = summary.get("stagesSummary");
                if (stagesSummary != null && stagesSummary.isArray()) {
                    for (JsonNode s : stagesSummary) {
                        String type = s.path("type").asText(null);
                        Integer minutes = intField(s, "minutes");
                        if (type == null || minutes == null) continue;
                        parStade.merge(type, minutes, Integer::sum);
                        if ("AWAKE".equals(type)) {
                            Integer count = intField(s, "count");
                            if (count != null) nbReveils = count;
                        }
                    }
                }
            }

            result.add(new SommeilBrut(externalId, debut, fin, date, sieste, stadesDisponibles,
                    minutesEndormi, minutesEveilleSummary != null ? minutesEveilleSummary : parStade.get("AWAKE"),
                    minutesAvant, minutesApres, parStade.get("DEEP"), parStade.get("LIGHT"), parStade.get("REM"),
                    parStade.get("RESTLESS"), nbReveils));
        }
        return result;
    }

    private static LocalDate dataPointDate(JsonNode dataPoint, JsonNode value) {
        LocalDate d = FitbitParser.googleDate(value.get("date"));
        if (d != null) return d;
        return FitbitParser.googleDate(dataPoint.get("date"));
    }

    private static Double doubleField(JsonNode obj, String... names) {
        if (obj == null) return null;
        for (String n : names) {
            JsonNode f = obj.get(n);
            if (f == null || f.isNull() || f.isObject() || f.isArray()) continue;
            if (f.isNumber()) return f.asDouble();
            try {
                return Double.parseDouble(f.asText().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Integer intField(JsonNode obj, String name) {
        Long l = FitbitParser.longField(obj, name);
        return l != null ? l.intValue() : null;
    }

    private static Instant instantField(JsonNode obj, String name) {
        if (obj == null) return null;
        JsonNode f = obj.get(name);
        if (f == null || !f.isTextual()) return null;
        try {
            return Instant.parse(f.asText());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate asLocalDate(Instant instant) {
        return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
