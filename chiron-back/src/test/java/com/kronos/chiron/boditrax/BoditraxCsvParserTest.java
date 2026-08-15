package com.kronos.chiron.boditrax;

import com.kronos.chiron.visbody.dto.VisbodyReport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BoditraxCsvParserTest {

    private static final String SCAN_DATE = "6/2/2026 11:18:33 AM";
    private static final LocalDateTime SCAN_INSTANT = LocalDateTime.of(2026, 6, 2, 11, 18, 33);

    private final BoditraxCsvParser parser = new BoditraxCsvParser();

    private BoditraxCsvParser.ParsedBoditrax parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static String scanSection(String... metrics) {
        StringBuilder sb = new StringBuilder("User Scan Details\nBodyMetricTypeId,Value,CreatedDate\n");
        for (String metric : metrics) {
            sb.append(metric).append('\n');
        }
        return sb.toString();
    }

    @Test
    void parse_emptyFile_returnsNoScan() {
        BoditraxCsvParser.ParsedBoditrax parsed = parse("");

        assertThat(parsed.scans()).isEmpty();
        assertThat(parsed.gender()).isNull();
        assertThat(parsed.tailleCm()).isNull();
        assertThat(parsed.dateNaissance()).isNull();
    }

    @Test
    void parse_dataBeforeAnySection_isIgnored() {
        assertThat(parse("garbage,1,2\nmore,3,4\n").scans()).isEmpty();
    }

    @Test
    void parse_userDetails_readsGenderAndDateOfBirth() {
        String csv = "User Details\nEmail,Name,DateOfBirth,Gender\n"
                + "yvain@chiron.app,Yvain,1/15/1990 12:00:00 AM,Male\n";

        BoditraxCsvParser.ParsedBoditrax parsed = parse(csv);

        assertThat(parsed.gender()).isEqualTo("Male");
        assertThat(parsed.dateNaissance()).isEqualTo(LocalDate.of(1990, 1, 15));
    }

    @Test
    void parse_nameContainingAComma_stillReadsGenderAndDateFromTheEnd() {
        String csv = "User Details\nEmail,Name,DateOfBirth,Gender\n"
                + "yvain@chiron.app,\"Dupont, Yvain\",1/15/1990 12:00:00 AM,Female\n";

        BoditraxCsvParser.ParsedBoditrax parsed = parse(csv);

        assertThat(parsed.gender()).isEqualTo("Female");
        assertThat(parsed.dateNaissance()).isEqualTo(LocalDate.of(1990, 1, 15));
    }

    @Test
    void parse_physiqueSection_readsHeight() {
        String csv = "User Physique Details\nHeight,Something\n182.5,x\n";

        assertThat(parse(csv).tailleCm()).isEqualTo(182.5);
    }

    @Test
    void parse_loginSection_isIgnored() {
        String csv = "User Login Details\nDate,Ip\n6/2/2026 11:18:33 AM,127.0.0.1\n";

        assertThat(parse(csv).scans()).isEmpty();
    }

    @Test
    void parse_singleScan_mapsCoreMetrics() {
        String csv = scanSection(
                "BodyWeight,82.4," + SCAN_DATE,
                "MuscleMass,38.1," + SCAN_DATE,
                "FatMass,16.0," + SCAN_DATE,
                "BodyMassIndex,24.7," + SCAN_DATE);

        VisbodyReport report = parse(csv).scans().get(0);

        assertThat(report.getMesureLe()).isEqualTo(SCAN_INSTANT);
        assertThat(report.getPoids()).isEqualTo(82.4);
        assertThat(report.getMasseMusculaire()).isEqualTo(38.1);
        assertThat(report.getMgc()).isEqualTo(16.0);
        assertThat(report.getImc()).isEqualTo(24.7);
    }

    @Test
    void parse_metricsSharingATimestamp_areGroupedIntoOneScan() {
        String csv = scanSection(
                "BodyWeight,82.4," + SCAN_DATE,
                "FatMass,16.0," + SCAN_DATE);

        assertThat(parse(csv).scans()).hasSize(1);
    }

    @Test
    void parse_metricsAtDifferentTimestamps_produceOneScanEach() {
        String csv = scanSection(
                "BodyWeight,82.4," + SCAN_DATE,
                "BodyWeight,81.0,7/2/2026 9:00:00 AM");

        assertThat(parse(csv).scans()).hasSize(2);
    }

    @Test
    void parse_bodyFatPercentage_isComputedFromFatMassAndBodyWeight() {
        String csv = scanSection(
                "BodyWeight,80.0," + SCAN_DATE,
                "FatMass,16.0," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getTgcPct()).isCloseTo(20.0, within(0.0001));
    }

    @Test
    void parse_fatMassWithoutBodyWeight_leavesPercentageNull() {
        String csv = scanSection("FatMass,16.0," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getTgcPct()).isNull();
    }

    @Test
    void parse_zeroBodyWeight_doesNotDivideByZero() {
        String csv = scanSection(
                "BodyWeight,0," + SCAN_DATE,
                "FatMass,16.0," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getTgcPct()).isNull();
    }

    @Test
    void parse_basalMetabolicRate_isConvertedFromKilojoulesToKilocalories() {
        String csv = scanSection("BasalMetabolicRatekJ,8368," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getMbKcal()).isCloseTo(2000.0, within(0.5));
    }

    @Test
    void parse_waterMasses_produceTheEcwOverTbwRatio() {
        String csv = scanSection(
                "WaterMass,48.0," + SCAN_DATE,
                "ExtraCellularWaterMass,18.0," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getRatioEcwTbw()).isCloseTo(0.375, within(0.0001));
    }

    @Test
    void parse_skeletalMuscleMass_isRebuiltFromSmiAndHeight() {
        String csv = "User Physique Details\nHeight\n180\n"
                + scanSection("SarcopeniaSMI,8.5," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getMms()).isCloseTo(8.5 * 1.8 * 1.8, within(0.0001));
    }

    @Test
    void parse_heightOnlyInScanMetrics_isUsedForSkeletalMuscleMass() {
        String csv = scanSection(
                "SarcopeniaSMI,8.5," + SCAN_DATE,
                "Height,180," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getMms()).isCloseTo(8.5 * 1.8 * 1.8, within(0.0001));
    }

    @Test
    void parse_smiWithoutHeight_leavesSkeletalMuscleMassNull() {
        String csv = scanSection("SarcopeniaSMI,8.5," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getMms()).isNull();
    }

    @Test
    void parse_boditraxScore_isRoundedIntoTheNoteField() {
        String csv = scanSection("BoditraxScore,72.6," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getNote()).isEqualTo(73);
    }

    @Test
    void parse_segmentMetrics_areMappedToTheirLimbs() {
        String csv = scanSection(
                "LeftArmFatMass,1.1," + SCAN_DATE,
                "RightArmFatMass,1.2," + SCAN_DATE,
                "TrunkFatMass,8.0," + SCAN_DATE,
                "LeftLegFatMass,2.5," + SCAN_DATE,
                "RightLegFatMass,2.6," + SCAN_DATE,
                "LeftArmMuscleMass,3.1," + SCAN_DATE,
                "RightArmMuscleMass,3.2," + SCAN_DATE,
                "TrunkMuscleMass,20.0," + SCAN_DATE,
                "LeftLegMuscleMass,9.5," + SCAN_DATE,
                "RightLegMuscleMass,9.6," + SCAN_DATE);

        VisbodyReport report = parse(csv).scans().get(0);

        assertThat(report.getMgcBrasGauche()).isEqualTo(1.1);
        assertThat(report.getMgcBrasDroit()).isEqualTo(1.2);
        assertThat(report.getMgcTronc()).isEqualTo(8.0);
        assertThat(report.getMgcJambeGauche()).isEqualTo(2.5);
        assertThat(report.getMgcJambeDroite()).isEqualTo(2.6);
        assertThat(report.getMuscleBrasGauche()).isEqualTo(3.1);
        assertThat(report.getMuscleBrasDroit()).isEqualTo(3.2);
        assertThat(report.getMuscleTronc()).isEqualTo(20.0);
        assertThat(report.getMuscleJambeGauche()).isEqualTo(9.5);
        assertThat(report.getMuscleJambeDroite()).isEqualTo(9.6);
    }

    @Test
    void parse_narrowNoBreakSpaceBeforeAmPm_isStillParsed() {
        String csv = scanSection("BodyWeight,82.4,6/2/2026 11:18:33\u202fAM");

        assertThat(parse(csv).scans()).hasSize(1);
        assertThat(parse(csv).scans().get(0).getMesureLe()).isEqualTo(SCAN_INSTANT);
    }

    @Test
    void parse_nonBreakingSpaceBeforeAmPm_isStillParsed() {
        String csv = scanSection("BodyWeight,82.4,6/2/2026 11:18:33\u00a0AM");

        assertThat(parse(csv).scans()).hasSize(1);
        assertThat(parse(csv).scans().get(0).getMesureLe()).isEqualTo(SCAN_INSTANT);
    }

    @Test
    void parse_unparsableDate_dropsTheMetric() {
        String csv = scanSection("BodyWeight,82.4,not-a-date");

        assertThat(parse(csv).scans()).isEmpty();
    }

    @Test
    void parse_nonNumericValue_dropsTheMetric() {
        String csv = scanSection("BodyWeight,abc," + SCAN_DATE);

        assertThat(parse(csv).scans()).isEmpty();
    }

    @Test
    void parse_quotedValues_areUnquoted() {
        String csv = scanSection("BodyWeight,\"82.4\",\"" + SCAN_DATE + "\"");

        assertThat(parse(csv).scans().get(0).getPoids()).isEqualTo(82.4);
    }

    @Test
    void parse_byteOrderMark_doesNotHideTheFirstSection() {
        String csv = "﻿User Physique Details\nHeight\n180\n";

        assertThat(parse(csv).tailleCm()).isEqualTo(180.0);
    }

    @Test
    void parse_windowsLineEndings_areSupported() {
        String csv = "User Scan Details\r\nBodyMetricTypeId,Value,CreatedDate\r\n"
                + "BodyWeight,82.4," + SCAN_DATE + "\r\n";

        assertThat(parse(csv).scans()).hasSize(1);
    }

    @Test
    void parse_blankLines_areSkipped() {
        String csv = "\n\nUser Scan Details\nBodyMetricTypeId,Value,CreatedDate\n\n"
                + "BodyWeight,82.4," + SCAN_DATE + "\n\n";

        assertThat(parse(csv).scans()).hasSize(1);
    }

    @Test
    void parse_genderAndHeight_arePropagatedOntoEachScan() {
        String csv = "User Details\nEmail,Name,DateOfBirth,Gender\n"
                + "y@c.app,Yvain,1/15/1990 12:00:00 AM,Male\n"
                + "User Physique Details\nHeight\n180\n"
                + scanSection("BodyWeight,82.4," + SCAN_DATE);

        VisbodyReport report = parse(csv).scans().get(0);

        assertThat(report.getSexe()).isEqualTo("Male");
        assertThat(report.getTailleCm()).isEqualTo(180.0);
    }

    @Test
    void parse_ageMetric_isRounded() {
        String csv = scanSection("Age,35.7," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getAge()).isEqualTo(36);
    }

    @Test
    void parse_boneMass_isMappedToInorganicSalt() {
        String csv = scanSection("BoneMass,3.2," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getSelInorganique()).isEqualTo(3.2);
    }

    @Test
    void parse_scanOrder_followsFirstAppearance() {
        String csv = scanSection(
                "BodyWeight,80.0,7/2/2026 9:00:00 AM",
                "BodyWeight,82.4," + SCAN_DATE);

        assertThat(parse(csv).scans().get(0).getMesureLe())
                .isEqualTo(LocalDateTime.of(2026, 7, 2, 9, 0, 0));
        assertThat(parse(csv).scans().get(1).getMesureLe()).isEqualTo(SCAN_INSTANT);
    }
}
