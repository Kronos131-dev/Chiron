package com.kronos.chiron.fitbit.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleHealthParserTest {

    private final JsonMapper json = new JsonMapper();

    @Test
    void dailyRollupByDate_readsNamedCandidateField() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":10}},"distance":{"metersSum":"4200"}}
                ]}""");

        Map<LocalDate, Double> result = GoogleHealthParser.dailyRollupByDate(node, "distance", "metersSum", "sum");

        assertThat(result.get(LocalDate.of(2026, 8, 10))).isEqualTo(4200.0);
    }

    @Test
    void dailyRollupByDate_fallsBackToFirstNumberWhenCandidatesAbsent() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":10}},"totalCalories":{"unknownField":"1800"}}
                ]}""");

        Map<LocalDate, Double> result = GoogleHealthParser.dailyRollupByDate(node, "totalCalories", "kcalSum");

        assertThat(result.get(LocalDate.of(2026, 8, 10))).isEqualTo(1800.0);
    }

    @Test
    void dailyRollupByDate_sumsMultiplePointsOnSameDate() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":10}},"steps":{"countSum":"100"}},
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":10}},"steps":{"countSum":"50"}}
                ]}""");

        Map<LocalDate, Double> result = GoogleHealthParser.dailyRollupByDate(node, "steps", "countSum");

        assertThat(result.get(LocalDate.of(2026, 8, 10))).isEqualTo(150.0);
    }

    @Test
    void dailyRollupByDate_nullOrEmpty_returnsEmpty() {
        assertThat(GoogleHealthParser.dailyRollupByDate(null, "steps", "countSum")).isEmpty();
        assertThat(GoogleHealthParser.dailyRollupByDate(json.readTree("{}"), "steps", "countSum")).isEmpty();
    }

    @Test
    void zoneMinutesByDate_bucketsByZoneType() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":10}},"timeInHeartRateZone":[
                    {"heartRateZone":"FAT_BURN","durationSum":"600"},
                    {"heartRateZone":"CARDIO","durationSum":"300"},
                    {"heartRateZone":"PEAK","durationSum":"120"}
                  ]}
                ]}""");

        Map<LocalDate, GoogleHealthParser.ZoneMinutes> result = GoogleHealthParser.zoneMinutesByDate(node);

        GoogleHealthParser.ZoneMinutes zones = result.get(LocalDate.of(2026, 8, 10));
        assertThat(zones.bruleuse()).isEqualTo(10);
        assertThat(zones.cardio()).isEqualTo(5);
        assertThat(zones.pic()).isEqualTo(2);
    }

    @Test
    void zoneMinutesByDate_empty_returnsEmpty() {
        assertThat(GoogleHealthParser.zoneMinutesByDate(null)).isEmpty();
    }

    @Test
    void heartRateBuckets_readsMinAvgMaxAndCount() {
        JsonNode node = json.readTree(
                """
                        {"rollupDataPoints":[
                          {"startTime":"2026-08-10T06:00:00Z","heartRate":{"beatsPerMinuteMin":"58","beatsPerMinuteAvg":"64","beatsPerMinuteMax":"72","sampleCount":"5"}}
                        ]}""");

        List<GoogleHealthParser.FcBucket> buckets = GoogleHealthParser.heartRateBuckets(node);

        assertThat(buckets).hasSize(1);
        GoogleHealthParser.FcBucket b = buckets.get(0);
        assertThat(b.debut()).isEqualTo(Instant.parse("2026-08-10T06:00:00Z"));
        assertThat(b.min()).isEqualTo(58);
        assertThat(b.moyenne()).isEqualTo(64.0);
        assertThat(b.max()).isEqualTo(72);
        assertThat(b.nbEchantillons()).isEqualTo(5);
    }

    @Test
    void heartRateBuckets_missingStartTime_isSkipped() {
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"heartRate":{"beatsPerMinuteAvg":"64"}}
                ]}""");

        assertThat(GoogleHealthParser.heartRateBuckets(node)).isEmpty();
    }

    @Test
    void hrvByDate_readsAverageAndDeepSleepFields() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"dailyHeartRateVariability":{"date":{"year":2026,"month":8,"day":10},
                    "averageHeartRateVariabilityMilliseconds":42.5,
                    "deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds":38.1}}
                ]}""");

        Map<LocalDate, GoogleHealthParser.VfcJour> result = GoogleHealthParser.hrvByDate(node);

        GoogleHealthParser.VfcJour vfc = result.get(LocalDate.of(2026, 8, 10));
        assertThat(vfc.vfcMs()).isEqualTo(42.5);
        assertThat(vfc.vfcSommeilProfondMs()).isEqualTo(38.1);
    }

    @Test
    void zoneMinutesByDate_realGooglePayload_readsEveryZone() {
        // Given : capture réelle d'un compte Google Health. Les zones sont imbriquées dans
        // timeInHeartRateZones, les durées sont au format Duration protobuf, et les noms
        // sont LIGHT/MODERATE/VIGOROUS/PEAK et non FAT_BURN/CARDIO.
        JsonNode node = json.readTree("""
                {"rollupDataPoints":[
                  {"civilStartTime":{"date":{"year":2026,"month":8,"day":19},"time":{}},
                   "timeInHeartRateZone":{"timeInHeartRateZones":[
                     {"heartRateZone":"LIGHT","duration":"47520s"},
                     {"heartRateZone":"MODERATE","duration":"2580s"},
                     {"heartRateZone":"VIGOROUS","duration":"2220s"},
                     {"heartRateZone":"PEAK","duration":"60s"}
                   ]}}
                ]}""");

        // When
        Map<LocalDate, GoogleHealthParser.ZoneMinutes> result = GoogleHealthParser.zoneMinutesByDate(node);

        // Then
        GoogleHealthParser.ZoneMinutes zones = result.get(LocalDate.of(2026, 8, 19));
        assertThat(zones.bruleuse()).isEqualTo(43);
        assertThat(zones.cardio()).isEqualTo(37);
        assertThat(zones.pic()).isEqualTo(1);
    }

    @Test
    void dailyDoubleByDate_readsNamedCandidateField() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"dailyRespiratoryRate":{"date":{"year":2026,"month":8,"day":10},"breathsPerMinute":14.2}}
                ]}""");

        Map<LocalDate, Double> result = GoogleHealthParser.dailyDoubleByDate(node, "dailyRespiratoryRate",
                "breathsPerMinute", "value");

        assertThat(result.get(LocalDate.of(2026, 8, 10))).isEqualTo(14.2);
    }

    @Test
    void sleepSessions_realNight_agitationIsTheSumOfShortAwakenings() {
        // Given : capture réelle de la nuit du 19/08. Google affiche 21 min d'éveil et
        // 18 min d'agitation ; les quinze micro-réveils totalisent 1110 s, soit 18 min.
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-18T21:11:00Z","endTime":"2026-08-19T05:47:00Z"},
                    "type":"STAGES",
                    "metadata":{"stagesStatus":"SUCCEEDED","mainSleep":true},
                    "summary":{
                      "minutesInSleepPeriod":"516","minutesAsleep":"494","minutesAwake":"22",
                      "minutesToFallAsleep":"0","minutesAfterWakeUp":"0",
                      "stagesSummary":[
                        {"type":"AWAKE","minutes":"21","count":"3"},
                        {"type":"LIGHT","minutes":"290","count":"14"},
                        {"type":"DEEP","minutes":"91","count":"5"},
                        {"type":"REM","minutes":"113","count":"7"}
                      ]
                    },
                    "shortAwakenings":[
                      {"startTime":"2026-08-18T23:00:30Z","endTime":"2026-08-18T23:01:00Z"},
                      {"startTime":"2026-08-18T23:48:30Z","endTime":"2026-08-18T23:50:30Z"},
                      {"startTime":"2026-08-19T00:17:30Z","endTime":"2026-08-19T00:18:00Z"},
                      {"startTime":"2026-08-19T00:34:30Z","endTime":"2026-08-19T00:35:00Z"},
                      {"startTime":"2026-08-19T00:43:00Z","endTime":"2026-08-19T00:43:30Z"},
                      {"startTime":"2026-08-19T01:15:00Z","endTime":"2026-08-19T01:17:30Z"},
                      {"startTime":"2026-08-19T01:21:00Z","endTime":"2026-08-19T01:21:30Z"},
                      {"startTime":"2026-08-19T01:48:30Z","endTime":"2026-08-19T01:52:30Z"},
                      {"startTime":"2026-08-19T02:50:00Z","endTime":"2026-08-19T02:50:30Z"},
                      {"startTime":"2026-08-19T03:11:30Z","endTime":"2026-08-19T03:13:00Z"},
                      {"startTime":"2026-08-19T03:16:30Z","endTime":"2026-08-19T03:17:00Z"},
                      {"startTime":"2026-08-19T03:19:00Z","endTime":"2026-08-19T03:20:30Z"},
                      {"startTime":"2026-08-19T03:30:00Z","endTime":"2026-08-19T03:32:00Z"},
                      {"startTime":"2026-08-19T05:23:30Z","endTime":"2026-08-19T05:24:00Z"},
                      {"startTime":"2026-08-19T05:42:30Z","endTime":"2026-08-19T05:43:30Z"}
                    ]
                  }}
                ]}""");

        // When
        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        // Then
        GoogleHealthParser.SommeilBrut nuit = sessions.get(0);
        assertThat(nuit.minutesAgite()).isEqualTo(18);
        assertThat(nuit.minutesEveille()).isEqualTo(21);
        assertThat(nuit.minutesEndormi()).isEqualTo(494);
        assertThat(nuit.minutesProfond()).isEqualTo(91);
        assertThat(nuit.minutesLeger()).isEqualTo(290);
        assertThat(nuit.minutesParadoxal()).isEqualTo(113);
        assertThat(nuit.nbReveils()).isEqualTo(3);
    }

    @Test
    void sleepSessions_agitationIsAStageUnderAnotherName_isCounted() {
        // Given : nuit réelle du 19/08. Google affiche 21 min d'éveil ET 18 min
        // d'agitation, donc deux stades distincts — le second n'étant pas nommé RESTLESS,
        // il était purement ignoré et l'écran affichait « Agité 0 min ».
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-18T21:30:00Z","endTime":"2026-08-19T06:22:00Z"},
                    "metadata":{"stagesStatus":"SUCCEEDED"},
                    "summary":{
                      "minutesAsleep":494,
                      "minutesAwake":21,
                      "stagesSummary":[
                        {"type":"DEEP","minutes":91},
                        {"type":"LIGHT","minutes":290},
                        {"type":"REM","minutes":113},
                        {"type":"AWAKE","minutes":21,"count":5},
                        {"type":"MOVING","minutes":18}
                      ]
                    }
                  }}
                ]}""");

        // When
        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        // Then
        assertThat(sessions.get(0).minutesAgite()).isEqualTo(18);
        assertThat(sessions.get(0).minutesEveille()).isEqualTo(21);
    }

    @Test
    void sleepSessions_unknownStageIsAnAggregate_fallsBackToTheResidue() {
        // Given : un « ASLEEP » qui recompte le sommeil ferait dépasser le temps au lit.
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-18T22:00:00Z","endTime":"2026-08-19T06:00:00Z"},
                    "metadata":{"stagesStatus":"SUCCEEDED"},
                    "summary":{
                      "minutesAsleep":442,
                      "minutesAwake":15,
                      "minutesToFallAsleep":5,
                      "stagesSummary":[
                        {"type":"DEEP","minutes":100},
                        {"type":"LIGHT","minutes":250},
                        {"type":"REM","minutes":92},
                        {"type":"AWAKE","minutes":15},
                        {"type":"ASLEEP","minutes":442}
                      ]
                    }
                  }}
                ]}""");

        // When
        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        // Then
        assertThat(sessions.get(0).minutesAgite()).isEqualTo(18);
    }

    @Test
    void sleepSessions_noRestlessStage_derivesAgitationFromTheUnaccountedTime() {
        // Given : 480 min au lit, dont 442 expliquées par les stades, l'éveil et
        // l'endormissement. Google ne publie pas de stade RESTLESS, mais son application
        // affiche bien les 18 minutes qui restent.
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-18T21:00:00Z","endTime":"2026-08-19T05:00:00Z"},
                    "metadata":{"stagesStatus":"SUCCEEDED"},
                    "summary":{
                      "minutesAsleep":442,
                      "minutesAwake":15,
                      "minutesToFallAsleep":5,
                      "stagesSummary":[
                        {"type":"DEEP","minutes":100},
                        {"type":"LIGHT","minutes":250},
                        {"type":"REM","minutes":92},
                        {"type":"AWAKE","minutes":15,"count":6}
                      ]
                    }
                  }}
                ]}""");

        // When
        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        // Then
        assertThat(sessions.get(0).minutesAgite()).isEqualTo(18);
        assertThat(sessions.get(0).nbReveils()).isEqualTo(6);
    }

    @Test
    void sleepSessions_stagesAccountForEveryMinute_leavesAgitationAtZero() {
        // Given
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-18T22:00:00Z","endTime":"2026-08-19T04:00:00Z"},
                    "metadata":{"stagesStatus":"SUCCEEDED"},
                    "summary":{
                      "minutesAsleep":350,
                      "minutesAwake":10,
                      "stagesSummary":[
                        {"type":"DEEP","minutes":80},
                        {"type":"LIGHT","minutes":200},
                        {"type":"REM","minutes":70},
                        {"type":"AWAKE","minutes":10}
                      ]
                    }
                  }}
                ]}""");

        // When
        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        // Then
        assertThat(sessions.get(0).minutesAgite()).isZero();
    }

    @Test
    void sleepSessions_readsStagesMetadataAndSummary() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-09T22:30:00Z","endTime":"2026-08-10T06:30:00Z"},
                    "metadata":{"externalId":"abc-123","nap":false,"stagesStatus":"SUCCEEDED"},
                    "summary":{
                      "minutesAsleep":420,
                      "minutesAwake":15,
                      "minutesToFallAsleep":10,
                      "minutesAfterWakeUp":5,
                      "stagesSummary":[
                        {"type":"DEEP","minutes":90,"count":4},
                        {"type":"LIGHT","minutes":220,"count":10},
                        {"type":"REM","minutes":110,"count":6},
                        {"type":"AWAKE","minutes":15,"count":3}
                      ]
                    }
                  }}
                ]}""");

        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        assertThat(sessions).hasSize(1);
        GoogleHealthParser.SommeilBrut s = sessions.get(0);
        assertThat(s.externalId()).isEqualTo("abc-123");
        assertThat(s.sieste()).isFalse();
        assertThat(s.stadesDisponibles()).isTrue();
        assertThat(s.minutesEndormi()).isEqualTo(420);
        assertThat(s.minutesProfond()).isEqualTo(90);
        assertThat(s.minutesLeger()).isEqualTo(220);
        assertThat(s.minutesParadoxal()).isEqualTo(110);
        assertThat(s.minutesEveille()).isEqualTo(15);
        assertThat(s.nbReveils()).isEqualTo(3);
        assertThat(s.minutesAvantEndormissement()).isEqualTo(10);
        assertThat(s.minutesApresReveil()).isEqualTo(5);
        assertThat(s.date()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void sleepSessions_stagesStatusNotSucceeded_marksStadesUnavailable() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-09T22:30:00Z","endTime":"2026-08-10T06:30:00Z"},
                    "metadata":{"nap":false,"stagesStatus":"REJECTED_COVERAGE"},
                    "summary":{"minutesAsleep":420}
                  }}
                ]}""");

        List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(node);

        assertThat(sessions.get(0).stadesDisponibles()).isFalse();
        assertThat(sessions.get(0).minutesEndormi()).isEqualTo(420);
    }

    @Test
    void sleepSessions_nap_isFlaggedAsSieste() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"sleep":{
                    "interval":{"startTime":"2026-08-10T13:00:00Z","endTime":"2026-08-10T13:30:00Z"},
                    "metadata":{"nap":true,"stagesStatus":"SUCCEEDED"},
                    "summary":{"minutesAsleep":30}
                  }}
                ]}""");

        assertThat(GoogleHealthParser.sleepSessions(node).get(0).sieste()).isTrue();
    }

    @Test
    void exerciseSessions_realWeightMachinesPayload_readsMetricsAndZones() {
        JsonNode node = json.readTree(
                """
                        {"dataPoints":[
                          {"name":"users/4369363054743728680/dataTypes/exercise/dataPoints/3327996276727937408",
                           "dataSource":{"recordingMethod":"MANUAL","device":{"formFactor":"PHONE"},"platform":"FITBIT"},
                           "exercise":{
                             "interval":{"startTime":"2026-08-18T10:15:00Z","endTime":"2026-08-18T11:30:00Z"},
                             "exerciseType":"WEIGHT_MACHINES",
                             "metricsSummary":{
                               "caloriesKcal":406,
                               "averageHeartRateBeatsPerMinute":"124",
                               "activeZoneMinutes":"73",
                               "heartRateZoneDurations":{"lightTime":"840s","moderateTime":"2760s","vigorousTime":"900s","peakTime":"0s"}
                             },
                             "displayName":"Appareils de musculation"
                           }}
                        ]}""");

        List<GoogleHealthParser.ExerciceBrut> sessions = GoogleHealthParser.exerciseSessions(node);

        assertThat(sessions).hasSize(1);
        GoogleHealthParser.ExerciceBrut ex = sessions.get(0);
        assertThat(ex.exerciseType()).isEqualTo("WEIGHT_MACHINES");
        assertThat(ex.debut()).isEqualTo(Instant.parse("2026-08-18T10:15:00Z"));
        assertThat(ex.fin()).isEqualTo(Instant.parse("2026-08-18T11:30:00Z"));
        assertThat(ex.caloriesKcal()).isEqualTo(406);
        assertThat(ex.fcMoyenne()).isEqualTo(124.0);
        assertThat(ex.activeZoneMinutes()).isEqualTo(73);
        assertThat(ex.minutesBasse()).isEqualTo(14);
        assertThat(ex.minutesBruleuse()).isEqualTo(46);
        assertThat(ex.minutesCardio()).isEqualTo(15);
        assertThat(ex.minutesPic()).isEqualTo(0);
        assertThat(ex.externalId())
                .isEqualTo("users/4369363054743728680/dataTypes/exercise/dataPoints/3327996276727937408");
    }

    @Test
    void exerciseSessions_walkingWithNoActiveZoneMinutes_optionalFieldsAreNull() {
        JsonNode node = json.readTree(
                """
                        {"dataPoints":[
                          {"name":"users/x/dataTypes/exercise/dataPoints/1",
                           "exercise":{
                             "interval":{"startTime":"2026-08-16T17:13:09.200Z","endTime":"2026-08-16T17:55:52.400Z"},
                             "exerciseType":"WALKING",
                             "metricsSummary":{
                               "caloriesKcal":306,
                               "averageHeartRateBeatsPerMinute":"80",
                               "heartRateZoneDurations":{"lightTime":"2520s","moderateTime":"0s","vigorousTime":"0s","peakTime":"0s"}
                             }
                           }}
                        ]}""");

        GoogleHealthParser.ExerciceBrut ex = GoogleHealthParser.exerciseSessions(node).get(0);

        assertThat(ex.exerciseType()).isEqualTo("WALKING");
        assertThat(ex.activeZoneMinutes()).isNull();
        assertThat(ex.minutesBasse()).isEqualTo(42);
    }

    @Test
    void exerciseSessions_missingInterval_isSkipped() {
        JsonNode node = json.readTree("""
                {"dataPoints":[
                  {"exercise":{"exerciseType":"RUNNING","metricsSummary":{"caloriesKcal":100}}}
                ]}""");

        assertThat(GoogleHealthParser.exerciseSessions(node)).isEmpty();
    }

    @Test
    void nextPageToken_presentAndAbsent() {
        assertThat(GoogleHealthParser.nextPageToken(json.readTree("{\"nextPageToken\":\"abc\"}"))).isEqualTo("abc");
        assertThat(GoogleHealthParser.nextPageToken(json.readTree("{}"))).isNull();
        assertThat(GoogleHealthParser.nextPageToken(null)).isNull();
    }
}
