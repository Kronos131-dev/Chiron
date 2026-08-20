package com.kronos.chiron.noctua.service.impl;

import com.kronos.chiron.noctua.model.NoctuaBriefingType;
import com.kronos.chiron.noctua.service.NoctuaBriefingService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoctuaVeilleServiceTest {

    // Réveil fixé à 09:00 le 20 août : suffisamment tard pour que le seuil du réveil (fin de
    // nuit + 10 min) et celui du coucher (21h30 par défaut - 45 min = 20h45) restent testables
    // indépendamment en avançant l'horloge dans chaque test.
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Mock
    private SanteSommeilRepository santeSommeilRepository;
    @Mock
    private SanteActiviteRepository santeActiviteRepository;
    @Mock
    private NoctuaBriefingService noctuaBriefingService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T07:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private NoctuaVeilleServiceImpl noctuaVeilleService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(7L).username("athlete").build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(eq(user), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void evaluer_nuitTermineeDepuisPlusDe10Minutes_genereLeBriefingReveil() {
        SanteSommeil nuit = SanteSommeil.builder().utilisateur(user).date(TODAY)
                .fin(LocalDateTime.of(2026, 8, 20, 8, 30)).score(80).build();
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, TODAY))
                .thenReturn(Optional.of(nuit));
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user,
                TODAY.minusDays(1))).thenReturn(Optional.empty());
        when(noctuaBriefingService.dejaProduit(eq(user), anyString())).thenReturn(false);
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of());

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService).genererSiNecessaire(eq(user), eq(NoctuaBriefingType.REVEIL), eq(TODAY),
                eq("REVEIL:" + TODAY), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_nuitTermineeDepuisMoinsDe10Minutes_neGenereRien() {
        SanteSommeil nuit = SanteSommeil.builder().utilisateur(user).date(TODAY)
                .fin(LocalDateTime.of(2026, 8, 20, 8, 55)).score(80).build();
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, TODAY))
                .thenReturn(Optional.of(nuit));
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user,
                TODAY.minusDays(1))).thenReturn(Optional.empty());
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of());

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService, never()).genererSiNecessaire(any(), eq(NoctuaBriefingType.REVEIL), any(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_briefingReveilDejaProduit_neReessaiePas() {
        SanteSommeil nuit = SanteSommeil.builder().utilisateur(user).date(TODAY)
                .fin(LocalDateTime.of(2026, 8, 20, 8, 30)).score(80).build();
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, TODAY))
                .thenReturn(Optional.of(nuit));
        when(noctuaBriefingService.dejaProduit(user, "REVEIL:" + TODAY)).thenReturn(true);
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of());

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService, never()).genererSiNecessaire(any(), eq(NoctuaBriefingType.REVEIL), any(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_activiteCompletDansLes24Heures_genereLeBriefingActivite() {
        SanteActivite activite = SanteActivite.builder().id(9L).utilisateur(user)
                .typeActivite(TypeActivite.MUSCULATION).source(SourceActivite.CHIRON_MUSCU)
                .startTime(LocalDateTime.of(2026, 8, 20, 6, 0)).endTime(LocalDateTime.of(2026, 8, 20, 6, 45))
                .statutEnrichissement(StatutEnrichissement.COMPLET).build();
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of(activite));
        when(noctuaBriefingService.dejaProduit(eq(user), anyString())).thenReturn(false);

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService).genererSiNecessaire(eq(user), eq(NoctuaBriefingType.ACTIVITE), any(),
                eq("ACTIVITE:9"), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_activiteDejaBriefee_neGenereQuUneFois() {
        SanteActivite activite = SanteActivite.builder().id(9L).utilisateur(user)
                .typeActivite(TypeActivite.COURSE).source(SourceActivite.GOOGLE_DETECTE)
                .startTime(LocalDateTime.of(2026, 8, 20, 6, 0)).endTime(LocalDateTime.of(2026, 8, 20, 6, 45))
                .statutEnrichissement(StatutEnrichissement.COMPLET).build();
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of(activite));
        when(noctuaBriefingService.dejaProduit(user, "ACTIVITE:9")).thenReturn(true);

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService, never()).genererSiNecessaire(any(), eq(NoctuaBriefingType.ACTIVITE), any(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_avantLeSeuilDeCoucherHabituel_neGenereRien() {
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of());
        when(noctuaBriefingService.dejaProduit(eq(user), anyString())).thenReturn(false);
        // 09:00 heure locale (07:00 UTC fixé) est bien avant le seuil par défaut 20h45.

        noctuaVeilleService.evaluer(user);

        verify(noctuaBriefingService, never()).genererSiNecessaire(any(), eq(NoctuaBriefingType.COUCHER), any(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void evaluer_apresLeSeuilDeCoucherHabituel_genereLeBriefingCoucher() {
        Clock soir = Clock.fixed(Instant.parse("2026-08-20T19:00:00Z"), ZoneId.of("Europe/Paris"));
        NoctuaVeilleServiceImpl service = new NoctuaVeilleServiceImpl(santeSommeilRepository,
                santeActiviteRepository, noctuaBriefingService, soir);
        when(santeActiviteRepository.findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(
                eq(user), eq(StatutEnrichissement.COMPLET), any(), any())).thenReturn(List.of());
        when(noctuaBriefingService.dejaProduit(eq(user), anyString())).thenReturn(false);

        service.evaluer(user);

        ArgumentCaptor<String> cleCaptor = ArgumentCaptor.forClass(String.class);
        verify(noctuaBriefingService).genererSiNecessaire(eq(user), eq(NoctuaBriefingType.COUCHER), eq(TODAY),
                cleCaptor.capture(), anyString(), anyString(), anyString());
        assertThat(cleCaptor.getValue()).isEqualTo("COUCHER:" + TODAY);
    }
}
