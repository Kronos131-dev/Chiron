package com.kronos.chiron.course.service.impl;

import com.kronos.chiron.course.dto.CourseMesuresDto;
import com.kronos.chiron.course.dto.CoursePointDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CourseGeometrieServiceImplTest {

    private static final double LAT_DEPART = 48.8566;
    private static final double LON_DEPART = 2.3522;
    private static final long T_DEPART = 1_700_000_000_000L;
    private static final double DEGRE_LATITUDE_EN_METRES = 2 * Math.PI * 6371008.8 / 360.0;

    private final CourseGeometrieServiceImpl service = new CourseGeometrieServiceImpl();

    private CoursePointDto pointApresMetres(double metres, long secondes, Double altitude) {
        return new CoursePointDto(
                LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
                LON_DEPART,
                T_DEPART + secondes * 1000L,
                altitude);
    }

    private List<CoursePointDto> ligneDroite(double metresTotal, int nbPoints, int dureeS) {
        List<CoursePointDto> points = new ArrayList<>();
        for (int i = 0; i < nbPoints; i++) {
            double fraction = (double) i / (nbPoints - 1);
            points.add(pointApresMetres(metresTotal * fraction, Math.round(dureeS * fraction), null));
        }
        return points;
    }

    private CoursePointDto repriseApresMetres(double metres, long secondes) {
        return new CoursePointDto(
                LAT_DEPART + metres / DEGRE_LATITUDE_EN_METRES,
                LON_DEPART,
                T_DEPART + secondes * 1000L,
                null,
                true);
    }

    @Test
    void mesurer_ligneDroiteDeUnKilometre_retrouveLaDistance() {
        // Given
        List<CoursePointDto> points = ligneDroite(1000.0, 11, 300);

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.distanceM()).isCloseTo(1000.0, within(2.0));
        assertThat(mesures.dureeS()).isEqualTo(300);
    }

    @Test
    void mesurer_deuxKilometresEtDemi_produitDeuxSplits() {
        // Given
        List<CoursePointDto> points = ligneDroite(2500.0, 251, 750);

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.splits()).hasSize(2);
        assertThat(mesures.splits().get(0).kilometre()).isEqualTo(1);
        assertThat(mesures.splits().get(0).dureeS()).isCloseTo(300, within(3));
        assertThat(mesures.splits().get(1).kilometre()).isEqualTo(2);
        assertThat(mesures.splits().get(1).dureeS()).isCloseTo(300, within(3));
    }

    @Test
    void mesurer_parcoursPlusCourtQuUnKilometre_neProduitAucunSplit() {
        // Given
        List<CoursePointDto> points = ligneDroite(600.0, 21, 200);

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.splits()).isEmpty();
    }

    // WHY: l'altitude GPS oscille de quelques mètres à l'arrêt. Le test verrouille le fait
    // qu'un parcours plat mais bruité ne produit aucun dénivelé.
    @Test
    void mesurer_altitudeBruiteeSurTerrainPlat_neCompteAucunDenivele() {
        // Given
        List<CoursePointDto> points = List.of(
                pointApresMetres(0, 0, 100.0),
                pointApresMetres(100, 30, 101.5),
                pointApresMetres(200, 60, 99.0),
                pointApresMetres(300, 90, 100.8),
                pointApresMetres(400, 120, 99.5));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.denivelePositifM()).isZero();
    }

    @Test
    void mesurer_monteeFranche_cumuleLeDenivelePositif() {
        // Given
        List<CoursePointDto> points = List.of(
                pointApresMetres(0, 0, 100.0),
                pointApresMetres(100, 30, 120.0),
                pointApresMetres(200, 60, 110.0),
                pointApresMetres(300, 90, 140.0));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.denivelePositifM()).isCloseTo(50.0, within(0.01));
    }

    @Test
    void mesurer_altitudesAbsentes_neCompteAucunDenivele() {
        // Given
        List<CoursePointDto> points = ligneDroite(500.0, 6, 150);

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.denivelePositifM()).isZero();
    }

    @Test
    void mesurer_pointUnique_retourneDesMesuresVides() {
        // Given
        List<CoursePointDto> points = List.of(pointApresMetres(0, 0, null));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.distanceM()).isZero();
        assertThat(mesures.dureeS()).isZero();
        assertThat(mesures.splits()).isEmpty();
    }

    @Test
    void mesurer_listeNulle_retourneDesMesuresVides() {
        // When
        CourseMesuresDto mesures = service.mesurer(null);

        // Then
        assertThat(mesures.distanceM()).isZero();
        assertThat(mesures.splits()).isEmpty();
    }

    // WHY: le tracker marque d'une coupure le premier point suivant une reprise. Le segment
    // franchi pendant la pause n'a pas été couru : ni sa longueur ni sa durée ne comptent.
    @Test
    void mesurer_repriseApresPause_ignoreLaDistanceParcouruePendantLaPause() {
        // Given
        List<CoursePointDto> points = List.of(
                pointApresMetres(0, 0, null),
                pointApresMetres(500, 150, null),
                repriseApresMetres(2500, 900),
                pointApresMetres(3000, 1050, null));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.distanceM()).isCloseTo(1000.0, within(2.0));
    }

    @Test
    void mesurer_repriseApresPause_retireLaDureeDeLaPause() {
        // Given
        List<CoursePointDto> points = List.of(
                pointApresMetres(0, 0, null),
                pointApresMetres(500, 150, null),
                repriseApresMetres(500, 900),
                pointApresMetres(1000, 1050, null));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.dureeS()).isEqualTo(300);
    }

    @Test
    void mesurer_pauseAuMilieuDUnKilometre_neGonflePasLeSplit() {
        // Given
        List<CoursePointDto> points = List.of(
                pointApresMetres(0, 0, null),
                pointApresMetres(500, 150, null),
                repriseApresMetres(500, 900),
                pointApresMetres(1000, 1050, null),
                pointApresMetres(1500, 1200, null));

        // When
        CourseMesuresDto mesures = service.mesurer(points);

        // Then
        assertThat(mesures.splits()).hasSize(1);
        assertThat(mesures.splits().get(0).dureeS()).isEqualTo(300);
    }

    @Test
    void allureKmh_unKilometreEnCinqMinutes_donneDouzeKmH() {
        assertThat(service.allureKmh(1000.0, 300)).isCloseTo(12.0, within(0.01));
    }

    @Test
    void allureKmh_dureeNulle_donneZero() {
        assertThat(service.allureKmh(1000.0, 0)).isZero();
    }
}
