package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.service.CaloriesEffortService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiviteFusionServiceImplTest {

    @Mock
    private SanteActiviteRepository santeActiviteRepository;
    @Mock
    private SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    @Mock
    private CaloriesEffortService caloriesEffortService;
    @Mock
    private SeuilsCardiaquesService seuilsCardiaquesService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T14:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private ActiviteFusionServiceImpl service;

    private Utilisateur user;

    // Seuils par défaut d'un adulte de 30 ans / FC repos 60 : modérée 110, intense 139, max 175
    // — les mêmes valeurs que les lignes fixes du graphe FC (coeur.ts).
    private static final SeuilsCardiaquesDto SEUILS = new SeuilsCardiaquesDto(110, 139, 175);

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        when(seuilsCardiaquesService.calculer(user)).thenReturn(SEUILS);
    }

    private SanteActivite chironActivite(LocalDateTime debut, LocalDateTime fin) {
        return SanteActivite.builder().id(9L).utilisateur(user).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION).startTime(debut).endTime(fin)
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE).build();
    }

    private SanteFrequenceCardiaque bucket(int fc, int echantillons) {
        return SanteFrequenceCardiaque.builder().fcMin(fc - 5).fcMoyenne((double) fc).fcMax(fc + 5)
                .nbEchantillons(echantillons).build();
    }

    // Reproduit exactement le cas de la capture : une séance Chiron de 46 minutes
    // (11:59-12:45) et un exercice Google de 26 minutes (12:25-12:51) entièrement contenu
    // dedans, avec ses propres chiffres (147 bpm, 161 kcal, charge 43) sur la mauvaise fenêtre.
    @Test
    void fusionnerActivite_googleContenueDansChiron_supprimeLaLigneGoogleEtRecalculeSurLaFenetreChiron() {
        LocalDateTime debut = LocalDateTime.of(2026, 8, 20, 11, 59);
        LocalDateTime fin = LocalDateTime.of(2026, 8, 20, 12, 45);
        SanteActivite chiron = chironActivite(debut, fin);

        SanteActivite google = SanteActivite.builder().id(20L).utilisateur(user).source(SourceActivite.GOOGLE_DETECTE)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 20, 12, 25))
                .endTime(LocalDateTime.of(2026, 8, 20, 12, 51))
                .externalId("google-ex-1").calories(161).fcMoyenne(147.0).chargeCardio(43.0)
                .statutEnrichissement(StatutEnrichissement.COMPLET).build();

        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(user), eq(SourceActivite.GOOGLE_DETECTE), any(), any())).thenReturn(List.of(google));

        // Buckets de 5 min sur toute la fenêtre Chiron (46 min ~ 10 buckets), intensité
        // homogène pour vérifier que la moyenne pondérée reste plausible.
        List<SanteFrequenceCardiaque> buckets = List.of(
                bucket(90, 10), bucket(130, 60), bucket(145, 60), bucket(150, 60), bucket(148, 60),
                bucket(140, 60), bucket(135, 60), bucket(130, 60), bucket(100, 20), bucket(85, 10));
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut,
                fin)).thenReturn(buckets);
        when(caloriesEffortService.estimer(user, buckets)).thenReturn(320);

        service.fusionnerActivite(chiron);

        verify(santeActiviteRepository).deleteAll(List.of(google));
        assertThat(chiron.getExternalId()).isEqualTo("google-ex-1");
        assertThat(chiron.getCalories()).isEqualTo(320);
        assertThat(chiron.getFcMoyenne()).isNotNull();
        assertThat(chiron.getChargeCardio()).isNotNull();
        assertThat(chiron.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.COMPLET);
        assertThat(chiron.getProchaineTentativeAt()).isNull();

        int totalMinutesZones = chiron.getMinutesZoneBasse() + chiron.getMinutesZoneBruleuse()
                + chiron.getMinutesZoneCardio() + chiron.getMinutesZonePic();
        assertThat(totalMinutesZones).isEqualTo(buckets.size() * 5);
    }

    @Test
    void fusionnerActivite_activiteGoogleDeborde_nEstPasSupprimee() {
        LocalDateTime debut = LocalDateTime.of(2026, 8, 20, 11, 59);
        LocalDateTime fin = LocalDateTime.of(2026, 8, 20, 12, 45);
        SanteActivite chiron = chironActivite(debut, fin);

        // Une marche qui commence pendant la séance mais se termine bien après — pas contenue,
        // donc jamais fusionnée ni supprimée.
        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(user), eq(SourceActivite.GOOGLE_DETECTE), any(), any())).thenReturn(List.of());
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut,
                fin)).thenReturn(List.of(bucket(120, 60)));
        when(caloriesEffortService.estimer(eq(user), any())).thenReturn(200);

        service.fusionnerActivite(chiron);

        verify(santeActiviteRepository, never()).deleteAll(any());
    }

    @Test
    void fusionnerActivite_aucunBucketFc_neMarquePasComplet() {
        LocalDateTime debut = LocalDateTime.of(2026, 8, 20, 11, 59);
        LocalDateTime fin = LocalDateTime.of(2026, 8, 20, 12, 45);
        SanteActivite chiron = chironActivite(debut, fin);

        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(user), eq(SourceActivite.GOOGLE_DETECTE), any(), any())).thenReturn(List.of());
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut,
                fin)).thenReturn(List.of());

        service.fusionnerActivite(chiron);

        assertThat(chiron.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(chiron.getCalories()).isNull();
        verify(caloriesEffortService, never()).estimer(any(), any());
    }

    @Test
    void fusionnerActivite_pasDeEndTime_estUnNoOp() {
        SanteActivite chiron = SanteActivite.builder().id(9L).utilisateur(user)
                .source(SourceActivite.CHIRON_MUSCU).typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 20, 11, 59))
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE).build();

        service.fusionnerActivite(chiron);

        verify(santeActiviteRepository, never()).save(any());
        verify(santeFrequenceCardiaqueRepository, never())
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(any(), any(), any());
    }

    @Test
    void fusionnerActivite_conserveLExternalIdExistantSurLaLigneChiron() {
        LocalDateTime debut = LocalDateTime.of(2026, 8, 20, 11, 59);
        LocalDateTime fin = LocalDateTime.of(2026, 8, 20, 12, 45);
        SanteActivite chiron = chironActivite(debut, fin);
        chiron.setExternalId("deja-connu");

        SanteActivite google = SanteActivite.builder().id(20L).utilisateur(user).source(SourceActivite.GOOGLE_DETECTE)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.of(2026, 8, 20, 12, 25))
                .endTime(LocalDateTime.of(2026, 8, 20, 12, 51))
                .externalId("google-ex-1").statutEnrichissement(StatutEnrichissement.COMPLET).build();
        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                eq(user), eq(SourceActivite.GOOGLE_DETECTE), any(), any())).thenReturn(List.of(google));
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut,
                fin)).thenReturn(List.of());

        service.fusionnerActivite(chiron);

        assertThat(chiron.getExternalId()).isEqualTo("deja-connu");
    }

    @Test
    void fusionnerFenetre_appelleFusionnerActivitePourChaqueLigneChironDeLaFenetre() {
        LocalDateTime debut = LocalDateTime.of(2026, 8, 20, 11, 59);
        LocalDateTime fin = LocalDateTime.of(2026, 8, 20, 12, 45);
        SanteActivite chiron1 = chironActivite(debut, fin);
        SanteActivite chiron2 = chironActivite(debut.minusDays(1), fin.minusDays(1));
        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeBetweenOrderByStartTimeAsc(eq(user),
                eq(SourceActivite.CHIRON_MUSCU), any(), any())).thenReturn(List.of(chiron1, chiron2));
        when(santeActiviteRepository.findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                any(), any(), any(), any())).thenReturn(List.of());
        when(santeFrequenceCardiaqueRepository.findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(any(), any(),
                any())).thenReturn(List.of());

        service.fusionnerFenetre(user, 7);

        verify(santeFrequenceCardiaqueRepository).findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user,
                chiron1.getStartTime(), chiron1.getEndTime());
        verify(santeFrequenceCardiaqueRepository).findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user,
                chiron2.getStartTime(), chiron2.getEndTime());
    }
}
