package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.dto.SanteActiviteDetailDto;
import com.kronos.chiron.sante.dto.SanteActiviteDto;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.PreparationService;
import com.kronos.chiron.sante.service.SeuilsCardiaquesService;
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
import java.time.LocalDateTime;
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
    @Mock
    private SanteActiviteRepository santeActiviteRepository;
    @Mock
    private PreparationService preparationService;
    @Mock
    private SeuilsCardiaquesService seuilsCardiaquesService;

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

    @Test
    void getActivites_noSourceFilter_returnsAllMappedToDto() {
        SanteActivite muscu = SanteActivite.builder()
                .id(1L).utilisateur(user).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 17, 18, 0))
                .endTime(LocalDateTime.of(2026, 8, 17, 19, 15))
                .fcMoyenne(124.0).statutEnrichissement(StatutEnrichissement.COMPLET)
                .build();
        SanteActivite marche = SanteActivite.builder()
                .id(2L).utilisateur(user).source(SourceActivite.GOOGLE_DETECTE)
                .typeActivite(TypeActivite.MARCHE)
                .startTime(LocalDateTime.of(2026, 8, 17, 7, 0))
                .endTime(LocalDateTime.of(2026, 8, 17, 7, 30))
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE)
                .build();
        when(santeActiviteRepository.findByUtilisateurAndStartTimeBetweenOrderByStartTimeDesc(eq(user), any(),
                any())).thenReturn(List.of(muscu, marche));

        List<SanteActiviteDto> result = santeQueryService.getActivites(user, 30, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SanteActiviteDto::typeActivite)
                .containsExactlyInAnyOrder(TypeActivite.MUSCULATION, TypeActivite.MARCHE);
        assertThat(result).filteredOn(a -> a.source() == SourceActivite.GOOGLE_DETECTE)
                .allMatch(SanteActiviteDto::enrichissementEnCours);
    }

    @Test
    void getActivites_sourceFilter_keepsOnlyMatchingSource() {
        SanteActivite muscu = SanteActivite.builder()
                .id(1L).utilisateur(user).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 17, 18, 0))
                .endTime(LocalDateTime.of(2026, 8, 17, 19, 15))
                .statutEnrichissement(StatutEnrichissement.COMPLET)
                .build();
        SanteActivite marche = SanteActivite.builder()
                .id(2L).utilisateur(user).source(SourceActivite.GOOGLE_DETECTE)
                .typeActivite(TypeActivite.MARCHE)
                .startTime(LocalDateTime.of(2026, 8, 17, 7, 0))
                .endTime(LocalDateTime.of(2026, 8, 17, 7, 30))
                .statutEnrichissement(StatutEnrichissement.COMPLET)
                .build();
        when(santeActiviteRepository.findByUtilisateurAndStartTimeBetweenOrderByStartTimeDesc(eq(user), any(),
                any())).thenReturn(List.of(muscu, marche));

        List<SanteActiviteDto> result = santeQueryService.getActivites(user, 30, SourceActivite.CHIRON_MUSCU);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source()).isEqualTo(SourceActivite.CHIRON_MUSCU);
    }

    @Test
    void getActiviteDetail_appartientAUtilisateur_returnsActiviteEtPointsFcEtSeuils() {
        SanteActivite activite = SanteActivite.builder()
                .id(5L).utilisateur(user).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 20, 11, 59))
                .endTime(LocalDateTime.of(2026, 8, 20, 12, 45))
                .statutEnrichissement(StatutEnrichissement.COMPLET)
                .build();
        when(santeActiviteRepository.findById(5L)).thenReturn(Optional.of(activite));
        SanteFrequenceCardiaque point = SanteFrequenceCardiaque.builder()
                .horodatage(LocalDateTime.of(2026, 8, 20, 12, 0)).fcMin(100).fcMoyenne(120.0).fcMax(140).build();
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user,
                activite.getStartTime(), activite.getEndTime())).thenReturn(List.of(point));
        SeuilsCardiaquesDto seuils = new SeuilsCardiaquesDto(110, 139, 175);
        when(seuilsCardiaquesService.calculer(user)).thenReturn(seuils);

        Optional<SanteActiviteDetailDto> result = santeQueryService.getActiviteDetail(user, 5L);

        assertThat(result).isPresent();
        assertThat(result.get().activite().id()).isEqualTo(5L);
        assertThat(result.get().pointsFrequenceCardiaque()).hasSize(1);
        assertThat(result.get().pointsFrequenceCardiaque().get(0).fcMoyenne()).isEqualTo(120.0);
        assertThat(result.get().seuils()).isEqualTo(seuils);
    }

    @Test
    void getActiviteDetail_appartientAUnAutreUtilisateur_returnsEmpty() {
        Utilisateur autre = Utilisateur.builder().id(99L).username("bob").build();
        SanteActivite activite = SanteActivite.builder()
                .id(5L).utilisateur(autre).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 20, 11, 59))
                .endTime(LocalDateTime.of(2026, 8, 20, 12, 45))
                .build();
        when(santeActiviteRepository.findById(5L)).thenReturn(Optional.of(activite));

        Optional<SanteActiviteDetailDto> result = santeQueryService.getActiviteDetail(user, 5L);

        assertThat(result).isEmpty();
    }

    @Test
    void getActiviteDetail_idInconnu_returnsEmpty() {
        when(santeActiviteRepository.findById(5L)).thenReturn(Optional.empty());

        Optional<SanteActiviteDetailDto> result = santeQueryService.getActiviteDetail(user, 5L);

        assertThat(result).isEmpty();
    }
}
