package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.dto.SanteAptitudeDto;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SanteQueryServiceImplTest {

    @Mock
    private FitbitService fitbitService;
    @Mock
    private SanteJourRepository santeJourRepository;
    @Mock
    private SanteSommeilRepository santeSommeilRepository;
    @Mock
    private SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    @Mock
    private SanteSyncStateRepository santeSyncStateRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private SanteQueryServiceImpl santeQueryService;

    private Utilisateur user;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        today = LocalDate.now(clock);
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of());
        when(santeSommeilRepository
                .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, today))
                .thenReturn(Optional.empty());
    }

    @Test
    void getAptitude_niveauPresentButVo2MaxNull_keepsTheDay() {
        // Given
        SanteJour jour = SanteJour.builder().utilisateur(user).date(today).niveauAptitude("GOOD").build();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(jour));

        // When
        List<SanteAptitudeDto> points = santeQueryService.getAptitude(user, 180);

        // Then
        assertThat(points).hasSize(1);
        assertThat(points.getFirst().vo2Max()).isNull();
        assertThat(points.getFirst().niveauAptitude()).isEqualTo("GOOD");
    }

    @Test
    void getAptitude_neitherVo2MaxNorNiveau_dropsTheDay() {
        // Given
        SanteJour jour = SanteJour.builder().utilisateur(user).date(today).build();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(jour));

        // When
        List<SanteAptitudeDto> points = santeQueryService.getAptitude(user, 180);

        // Then
        assertThat(points).isEmpty();
    }

    @Test
    void getResume_notLinked_returnsNotLinkedDto() {
        when(fitbitService.getStatus("athlete"))
                .thenReturn(new FitbitLinkStatus(false, false, null, null, null));

        SanteResumeDto dto = santeQueryService.getResume(user);

        assertThat(dto.linked()).isFalse();
        assertThat(dto.needsReconnect()).isFalse();
    }

    @Test
    void getResume_needsReconnect_returnsReconnectDto() {
        when(fitbitService.getStatus("athlete"))
                .thenReturn(new FitbitLinkStatus(true, true, null, null, null));

        SanteResumeDto dto = santeQueryService.getResume(user);

        assertThat(dto.linked()).isTrue();
        assertThat(dto.needsReconnect()).isTrue();
        assertThat(dto.pas()).isNull();
    }

    @Test
    void getResume_linkedWithTodayData_returnsFilledDto() {
        when(fitbitService.getStatus("athlete"))
                .thenReturn(new FitbitLinkStatus(true, false, "FBUSER", "activity", null));
        SanteJour jourAujourdhui = SanteJour.builder().utilisateur(user).date(today).pas(8500).fcRepos(58).build();
        when(santeJourRepository.findByUtilisateurAndDate(user, today)).thenReturn(Optional.of(jourAujourdhui));

        SanteResumeDto dto = santeQueryService.getResume(user);

        assertThat(dto.linked()).isTrue();
        assertThat(dto.pas()).isEqualTo(8500);
        assertThat(dto.fcRepos()).isEqualTo(58);
    }

    @Test
    void getResume_linkedNoDataYet_returnsNullMetricsWithoutError() {
        when(fitbitService.getStatus("athlete"))
                .thenReturn(new FitbitLinkStatus(true, false, "FBUSER", "activity", null));
        when(santeJourRepository.findByUtilisateurAndDate(user, today)).thenReturn(Optional.empty());

        SanteResumeDto dto = santeQueryService.getResume(user);

        assertThat(dto.linked()).isTrue();
        assertThat(dto.pas()).isNull();
    }

    @Test
    void getCardioHebdo_fewerThanFourPriorWeeks_leavesCibleNull() {
        List<SanteCardioHebdoDto> semaines = santeQueryService.getCardioHebdo(user, 1);

        assertThat(semaines).hasSize(1);
        assertThat(semaines.get(0).cibleBasse()).isNull();
        assertThat(semaines.get(0).cibleHaute()).isNull();
    }

    @Test
    void getCardioHebdo_fourPriorWeeksOfData_computesTargetBand() {
        LocalDate finSemaineCourante = today;
        LocalDate debutSemaineCourante = finSemaineCourante.minusDays(6);
        for (int i = 1; i <= 4; i++) {
            LocalDate debut = debutSemaineCourante.minusDays(7L * i);
            LocalDate fin = debut.plusDays(6);
            SanteJour jour = SanteJour.builder().utilisateur(user).date(debut).chargeCardio(100.0).build();
            when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, debut, fin))
                    .thenReturn(List.of(jour));
        }

        List<SanteCardioHebdoDto> semaines = santeQueryService.getCardioHebdo(user, 1);

        assertThat(semaines).hasSize(1);
        assertThat(semaines.get(0).cibleBasse()).isEqualTo(80.0);
        assertThat(semaines.get(0).cibleHaute()).isEqualTo(130.0);
    }
}
