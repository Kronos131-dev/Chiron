package com.kronos.chiron.visbody.service;

import com.kronos.chiron.visbody.dto.VisbodyReport;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VisbodyPdfParserTest {

    private final VisbodyPdfParser parser = new VisbodyPdfParser();

    private byte[] samplePdf() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/visbody/visbody-rapport.pdf")) {
            assertThat(in).as("fixture PDF présente").isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void parsesHeaderAndIdentity() throws Exception {
        VisbodyReport r = parser.parse(samplePdf());
        assertThat(r.getIdLabel()).isEqualTo("Tellier");
        assertThat(r.getMaskedEmail()).isEqualTo("octa****n1er@gmail...");
        assertThat(r.getSexe()).isEqualTo("Homme");
        assertThat(r.getTailleCm()).isEqualTo(176.0);
        assertThat(r.getAge()).isEqualTo(24);
        assertThat(r.getNote()).isEqualTo(90);
        assertThat(r.getMesureLe()).isEqualTo(LocalDateTime.of(2026, 5, 23, 14, 48, 41));
    }

    @Test
    void parsesMainMetrics() throws Exception {
        VisbodyReport r = parser.parse(samplePdf());
        assertThat(r.getPoids()).isEqualTo(79.9);
        assertThat(r.getMms()).isEqualTo(40.0);
        assertThat(r.getMgc()).isEqualTo(10.8);
        assertThat(r.getMmc()).isEqualTo(69.1);
        assertThat(r.getMasseMusculaire()).isEqualTo(66.0);
        assertThat(r.getTgcPct()).isEqualTo(13.5);
        assertThat(r.getImc()).isEqualTo(25.8);
        assertThat(r.getRth()).isEqualTo(0.87);
        assertThat(r.getMbKcal()).isEqualTo(1865.7);
        assertThat(r.getAgeMetabolique()).isEqualTo(23.0);
        assertThat(r.getGraisseViscerale()).isEqualTo(3.0);
        assertThat(r.getEauTotale()).isEqualTo(50.8);
        assertThat(r.getEauIntra()).isEqualTo(31.8);
        assertThat(r.getEauExtra()).isEqualTo(19.0);
        assertThat(r.getRatioEcwTbw()).isEqualTo(0.374);
        assertThat(r.getMasseProteine()).isEqualTo(13.8);
        assertThat(r.getSelInorganique()).isEqualTo(4.4);
    }

    @Test
    void parsesAllSegments() throws Exception {
        VisbodyReport r = parser.parse(samplePdf());
        // Masse grasse par segment (kg).
        assertThat(r.getMgcBrasGauche()).isEqualTo(0.21);
        assertThat(r.getMgcBrasDroit()).isEqualTo(0.24);
        assertThat(r.getMgcTronc()).isEqualTo(5.86);
        assertThat(r.getMgcJambeGauche()).isEqualTo(1.53);
        assertThat(r.getMgcJambeDroite()).isEqualTo(1.47);
        // Masse musculaire par segment (kg).
        assertThat(r.getMuscleBrasGauche()).isEqualTo(4.37);
        assertThat(r.getMuscleBrasDroit()).isEqualTo(4.50);
        assertThat(r.getMuscleTronc()).isEqualTo(32.51);
        assertThat(r.getMuscleJambeGauche()).isEqualTo(10.24);
        assertThat(r.getMuscleJambeDroite()).isEqualTo(10.43);
    }
}
