package com.kronos.chiron.course.service.impl;

import com.kronos.chiron.course.dto.CoursePointDto;
import com.kronos.chiron.course.dto.CourseTraceDto;
import com.kronos.chiron.course.dto.CourseTraceRequestDto;
import com.kronos.chiron.course.model.CourseTrace;
import com.kronos.chiron.course.persistence.CourseTraceRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseTraceServiceImplTest {

    private static final double LAT_DEPART = 48.8566;
    private static final double LON_DEPART = 2.3522;
    private static final long T_DEPART = 1_700_000_000_000L;
    private static final double DEGRE_LATITUDE_EN_METRES = 2 * Math.PI * 6371008.8 / 360.0;

    @Mock
    private CourseTraceRepository courseTraceRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneId.of("Europe/Paris"));

    private CourseTraceServiceImpl service;
    private Utilisateur athlete;

    @BeforeEach
    void setUp() {
        service = new CourseTraceServiceImpl(courseTraceRepository, new CourseGeometrieServiceImpl(), clock);
        athlete = Utilisateur.builder().id(7L).username("athlete").build();
    }

    private List<CoursePointDto> ligneDroite(double metresTotal, int nbPoints, int dureeS) {
        List<CoursePointDto> points = new ArrayList<>();
        for (int i = 0; i < nbPoints; i++) {
            double fraction = (double) i / (nbPoints - 1);
            points.add(new CoursePointDto(
                    LAT_DEPART + metresTotal * fraction / DEGRE_LATITUDE_EN_METRES,
                    LON_DEPART,
                    T_DEPART + Math.round(dureeS * fraction) * 1000L,
                    null));
        }
        return points;
    }

    @Test
    void enregistrer_traceValide_persisteLesMesuresRecalculees() {
        // Given
        List<CoursePointDto> points = ligneDroite(1000.0, 11, 300);
        when(courseTraceRepository.save(any(CourseTrace.class))).thenAnswer(invocation -> {
            CourseTrace trace = invocation.getArgument(0);
            trace.setId(42L);
            return trace;
        });

        // When
        CourseTraceDto resultat = service.enregistrer(athlete, new CourseTraceRequestDto(points));

        // Then
        ArgumentCaptor<CourseTrace> capture = ArgumentCaptor.forClass(CourseTrace.class);
        verify(courseTraceRepository).save(capture.capture());
        CourseTrace persistee = capture.getValue();

        assertThat(persistee.getUtilisateur()).isSameAs(athlete);
        assertThat(persistee.getNbPoints()).isEqualTo(11);
        assertThat(persistee.getDistanceM()).isCloseTo(1000.0, within(1.0));
        assertThat(persistee.getDureeS()).isEqualTo(300);
        assertThat(persistee.getPoints()).startsWith("[").contains("\"lat\"");
        assertThat(resultat.id()).isEqualTo(42L);
        assertThat(resultat.allureMoyenneKmh()).isCloseTo(12.0, within(0.05));
    }

    // WHY: le client calcule déjà distance et allure pour l'affichage direct. Le test verrouille
    // le fait que le serveur ne les reprend pas : seule sa propre mesure atteint le journal.
    @Test
    void enregistrer_traceValide_ignoreToutAgregatFourniParLeClient() {
        // Given
        List<CoursePointDto> points = ligneDroite(1000.0, 11, 300);
        when(courseTraceRepository.save(any(CourseTrace.class))).thenAnswer(i -> i.getArgument(0));

        // When
        CourseTraceDto resultat = service.enregistrer(athlete, new CourseTraceRequestDto(points));

        // Then
        assertThat(resultat.distanceM()).isCloseTo(1000.0, within(1.0));
        assertThat(resultat.splits()).hasSize(1);
    }

    @Test
    void enregistrer_moinsDeDeuxPoints_refuse() {
        // Given
        CourseTraceRequestDto requete = new CourseTraceRequestDto(
                List.of(new CoursePointDto(LAT_DEPART, LON_DEPART, T_DEPART, null)));

        // When / Then
        assertThatThrownBy(() -> service.enregistrer(athlete, requete))
                .isInstanceOf(ErrorResponseException.class);
        verify(courseTraceRepository, never()).save(any());
    }

    @Test
    void enregistrer_listeNulle_refuse() {
        // When / Then
        assertThatThrownBy(() -> service.enregistrer(athlete, new CourseTraceRequestDto(null)))
                .isInstanceOf(ErrorResponseException.class);
        verify(courseTraceRepository, never()).save(any());
    }

    @Test
    void enregistrer_tropDePoints_refuse() {
        // Given
        CourseTraceRequestDto requete = new CourseTraceRequestDto(ligneDroite(60000.0, 50001, 18000));

        // When / Then
        assertThatThrownBy(() -> service.enregistrer(athlete, requete))
                .isInstanceOf(ErrorResponseException.class);
        verify(courseTraceRepository, never()).save(any());
    }

    @Test
    void lire_traceDeLUtilisateur_relitLesPointsPersistes() {
        // Given
        List<CoursePointDto> points = ligneDroite(1000.0, 11, 300);
        when(courseTraceRepository.save(any(CourseTrace.class))).thenAnswer(i -> i.getArgument(0));
        service.enregistrer(athlete, new CourseTraceRequestDto(points));

        ArgumentCaptor<CourseTrace> capture = ArgumentCaptor.forClass(CourseTrace.class);
        verify(courseTraceRepository).save(capture.capture());
        CourseTrace persistee = capture.getValue();
        persistee.setId(42L);
        when(courseTraceRepository.findByIdAndUtilisateur(42L, athlete)).thenReturn(Optional.of(persistee));

        // When
        CourseTraceDto relue = service.lire(athlete, 42L);

        // Then
        assertThat(relue.points()).hasSize(11);
        assertThat(relue.points().get(0).lat()).isCloseTo(LAT_DEPART, within(0.000001));
        assertThat(relue.splits()).hasSize(1);
    }

    @Test
    void lire_traceDUnAutreUtilisateur_estIntrouvable() {
        // Given
        when(courseTraceRepository.findByIdAndUtilisateur(42L, athlete)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.lire(athlete, 42L))
                .isInstanceOf(ErrorResponseException.class);
    }
}
