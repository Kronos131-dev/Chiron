package com.kronos.chiron.fitbit.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FitbitClientTest {

    private static final String BASE_URL = "https://health.googleapis.com";

    private MockRestServiceServer mockServer;
    private FitbitClient fitbitClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        fitbitClient = new FitbitClient(builder, BASE_URL,
                "https://oauth2.googleapis.com/token", "https://chiron.test/callback", "client-id", "client-secret");
    }

    private static RequestMatcher requestPath(String path) {
        return request -> assertThat(request.getURI().getPath()).isEqualTo(path);
    }

    private static RequestMatcher filterQueryParam(String expectedFilter) {
        return request -> {
            String rawQuery = request.getURI().getRawQuery();
            assertThat(rawQuery).isNotNull();
            String decoded = URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
            assertThat(decoded).isEqualTo("filter=" + expectedFilter);
        };
    }

    @Test
    void rollUpDailySteps_sendsCivilTimeIntervalWithExclusiveEndPlusOneDay() {
        mockServer.expect(requestTo(BASE_URL + "/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.range.start.date.year").value(2026))
                .andExpect(jsonPath("$.range.start.date.month").value(8))
                .andExpect(jsonPath("$.range.start.date.day").value(5))
                .andExpect(jsonPath("$.range.end.date.year").value(2026))
                .andExpect(jsonPath("$.range.end.date.month").value(8))
                .andExpect(jsonPath("$.range.end.date.day").value(12))
                .andExpect(jsonPath("$.windowSizeDays").value(1))
                .andRespond(withSuccess("{\"rollupDataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.rollUpDailySteps("token", LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 11));

        mockServer.verify();
    }

    @Test
    void listRestingHeartRate_hitsKebabCasePathWithSnakeCaseFilter() {
        mockServer.expect(requestPath("/v4/users/me/dataTypes/daily-resting-heart-rate/dataPoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(filterQueryParam("daily_resting_heart_rate.date >= \"2026-08-05\""))
                .andRespond(withSuccess("{\"dataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.listRestingHeartRate("token", LocalDate.of(2026, 8, 5));

        mockServer.verify();
    }

    @Test
    void listSleep_filtersOnCivilEndTime() {
        mockServer.expect(requestPath("/v4/users/me/dataTypes/sleep/dataPoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(filterQueryParam("sleep.interval.civil_end_time >= \"2026-08-05\""))
                .andRespond(withSuccess("{\"dataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.listSleep("token", LocalDate.of(2026, 8, 5));

        mockServer.verify();
    }

    @Test
    void rollUpDailySteps_forbidden_throwsUnavailable() {
        mockServer.expect(requestTo(BASE_URL + "/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(
                () -> fitbitClient.rollUpDailySteps("token", LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5)))
                .isInstanceOf(FitbitClient.FitbitUnavailableException.class);
    }

    @Test
    void rollUpDailySteps_unauthorized_throwsUnauthorized() {
        mockServer.expect(requestTo(BASE_URL + "/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(
                () -> fitbitClient.rollUpDailySteps("token", LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5)))
                .isInstanceOf(FitbitClient.FitbitUnauthorizedException.class);
    }

    @Test
    void dailyRollUp_distance_hitsCorrectPathAndBody() {
        mockServer.expect(requestTo(BASE_URL + "/v4/users/me/dataTypes/distance/dataPoints:dailyRollUp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.range.start.date.day").value(5))
                .andExpect(jsonPath("$.range.end.date.day").value(7))
                .andExpect(jsonPath("$.windowSizeDays").value(1))
                .andRespond(withSuccess("{\"rollupDataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.dailyRollUp("token", GoogleHealthDataType.DISTANCE, LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6));

        mockServer.verify();
    }

    @Test
    void listDataPoints_dailyVo2Max_filtersOnDate() {
        mockServer.expect(requestPath("/v4/users/me/dataTypes/daily-vo2-max/dataPoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(filterQueryParam("daily_vo2_max.date >= \"2026-08-05\""))
                .andRespond(withSuccess("{\"dataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.listDataPoints("token", GoogleHealthDataType.DAILY_VO2_MAX, LocalDate.of(2026, 8, 5), null);

        mockServer.verify();
    }

    @Test
    void listDataPoints_withPageToken_addsPageTokenQueryParam() {
        mockServer.expect(requestPath("/v4/users/me/dataTypes/sleep/dataPoints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getURI().getQuery()).contains("pageToken=abc123"))
                .andRespond(withSuccess("{\"dataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.listDataPoints("token", GoogleHealthDataType.SLEEP, LocalDate.of(2026, 8, 5), "abc123");

        mockServer.verify();
    }

    @Test
    void rollUp_heartRate_sendsPhysicalTimeRangeAndWindowSizeAsDurationString() {
        mockServer.expect(requestTo(BASE_URL + "/v4/users/me/dataTypes/heart-rate/dataPoints:rollUp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.range.startTime").value("2026-08-05T00:00:00Z"))
                .andExpect(jsonPath("$.range.endTime").value("2026-08-06T00:00:00Z"))
                .andExpect(jsonPath("$.windowSize").value("300s"))
                .andRespond(withSuccess("{\"rollupDataPoints\":[]}", MediaType.APPLICATION_JSON));

        fitbitClient.rollUp("token", GoogleHealthDataType.HEART_RATE, Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-06T00:00:00Z"), 300);

        mockServer.verify();
    }
}
