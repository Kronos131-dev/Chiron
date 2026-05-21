package com.kronos.chiron.fitbit;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FitbitParserTest {

    private final JsonMapper json = new JsonMapper();

    @Test
    void stepsByDate_parsesDailyRollUp() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"year":2026,"month":5,"day":20},"steps":{"count":"8500"}},
                  {"civilStartTime":{"year":2026,"month":5,"day":21},"steps":{"count":"10200"}}
                ]}""");

        Map<LocalDate, Integer> steps = FitbitParser.stepsByDate(node);

        assertThat(steps.get(LocalDate.of(2026, 5, 20))).isEqualTo(8500);
        assertThat(steps.get(LocalDate.of(2026, 5, 21))).isEqualTo(10200);
    }

    @Test
    void sleepHoursByDate_parsesSleepDataPoints() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{"interval":{"endTime":"2026-05-20T07:00:00Z"},"summary":{"minutesAsleep":480}}},
                  {"sleep":{"interval":{"endTime":"2026-05-21T06:30:00Z"},"summary":{"minutesAsleep":450}}}
                ]}""");

        Map<LocalDate, Double> hours = FitbitParser.sleepHoursByDate(node);

        assertThat(hours.get(LocalDate.of(2026, 5, 20))).isEqualTo(8.0);
        assertThat(hours.get(LocalDate.of(2026, 5, 21))).isEqualTo(7.5);
    }

    @Test
    void restingHeartRateByDate_parsesDataPoints() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"dailyRestingHeartRate":{"beatsPerMinute":"58","date":{"year":2026,"month":5,"day":21}}}
                ]}""");

        Map<LocalDate, Integer> hr = FitbitParser.restingHeartRateByDate(node);

        assertThat(hr.get(LocalDate.of(2026, 5, 21))).isEqualTo(58);
    }

    @Test
    void parsers_emptyOrNull_returnEmpty() {
        assertThat(FitbitParser.stepsByDate(null)).isEmpty();
        assertThat(FitbitParser.sleepHoursByDate(json.readTree("{}"))).isEmpty();
        assertThat(FitbitParser.restingHeartRateByDate(json.readTree("{}"))).isEmpty();
    }
}
