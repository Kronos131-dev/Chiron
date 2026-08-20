package com.kronos.chiron.noctua.tools;

import com.kronos.chiron.coach.tools.ToolUserResolver;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.SanteSyncState;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutSync;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.SanteQueryService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SanteToolsTest {

    private static final String MEMORY_ID = "42";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);

    @Mock
    private SanteQueryService santeQueryService;
    @Mock
    private SanteJourRepository santeJourRepository;
    @Mock
    private SanteSommeilRepository santeSommeilRepository;
    @Mock
    private SanteActiviteRepository santeActiviteRepository;
    @Mock
    private SanteSyncStateRepository santeSyncStateRepository;
    @Mock
    private ToolUserResolver toolUserResolver;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private SanteTools santeTools;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(7L).username("athlete").build();
        when(toolUserResolver.load(MEMORY_ID)).thenReturn(user);
    }

    @Test
    void getResumeDuJour_aujourdhui_returnsResumeSentence() {
        SanteResumeDto resume = new SanteResumeDto(true, false, TODAY, 8000, 6000.0, 2200, 500, 45, 52, 62, 380.0, 71);
        when(santeQueryService.getResume(user)).thenReturn(resume);

        String result = santeTools.getResumeDuJour(MEMORY_ID, null);

        assertThat(result).contains("8000 pas").contains("52 bpm").contains("71/100");
    }

    @Test
    void getResumeDuJour_passeSansDonnee_saysNoData() {
        when(santeJourRepository.findByUtilisateurAndDate(user, TODAY.minusDays(3))).thenReturn(Optional.empty());

        String result = santeTools.getResumeDuJour(MEMORY_ID, "2026-08-17");

        assertThat(result).contains("Aucune donnée santé");
    }

    @Test
    void getNuit_nuitComplete_returnsSummaryWithScore() {
        SanteSommeil nuit = SanteSommeil.builder().utilisateur(user).date(TODAY).minutesEndormi(420)
                .nbReveils(2).fcSommeilMoyenne(58.0).score(78).scoreDuree(40).scoreComposition(20)
                .scoreRestauration(18).stadesDisponibles(false).build();
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user,
                TODAY)).thenReturn(Optional.of(nuit));

        String result = santeTools.getNuit(MEMORY_ID, null);

        assertThat(result).contains("7h00").contains("78/100");
    }

    @Test
    void getNuit_aucuneNuit_saysNoNight() {
        when(santeSommeilRepository.findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user,
                TODAY)).thenReturn(Optional.empty());

        String result = santeTools.getNuit(MEMORY_ID, null);

        assertThat(result).contains("Aucune nuit");
    }

    @Test
    void getDerniereActivite_activiteExistante_describesIt() {
        SanteActivite activite = SanteActivite.builder().id(5L).utilisateur(user)
                .typeActivite(TypeActivite.MUSCULATION).source(SourceActivite.CHIRON_MUSCU)
                .startTime(LocalDateTime.of(2026, 8, 20, 18, 0))
                .endTime(LocalDateTime.of(2026, 8, 20, 19, 0)).fcMoyenne(130.0).calories(400).build();
        when(santeActiviteRepository.findFirstByUtilisateurOrderByEndTimeDesc(user)).thenReturn(Optional.of(activite));

        String result = santeTools.getDerniereActivite(MEMORY_ID);

        assertThat(result).contains("#5").contains("60 min").contains("130 bpm");
    }

    @Test
    void getDerniereActivite_aucuneActivite_saysNoActivity() {
        when(santeActiviteRepository.findFirstByUtilisateurOrderByEndTimeDesc(user)).thenReturn(Optional.empty());

        String result = santeTools.getDerniereActivite(MEMORY_ID);

        assertThat(result).contains("Aucune activité");
    }

    @Test
    void getActivite_idAppartientAUnAutreUtilisateur_refuses() {
        Utilisateur autre = Utilisateur.builder().id(99L).username("bob").build();
        SanteActivite activite = SanteActivite.builder().id(5L).utilisateur(autre)
                .typeActivite(TypeActivite.COURSE).source(SourceActivite.GOOGLE_DETECTE)
                .startTime(LocalDateTime.now()).endTime(LocalDateTime.now()).build();
        when(santeActiviteRepository.findById(5L)).thenReturn(Optional.of(activite));

        String result = santeTools.getActivite(MEMORY_ID, 5L);

        assertThat(result).contains("Aucune activité #5");
    }

    @Test
    void getTendanceSante_ecartAMediane_reportsDeviation() {
        List<SanteJour> historique = fabriquerHistoriqueVfc();
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, TODAY.minusDays(29), TODAY))
                .thenReturn(historique);

        String result = santeTools.getTendanceSante(MEMORY_ID, null);

        assertThat(result).contains("VFC");
    }

    @Test
    void getTendanceSante_aucuneDonnee_saysNoData() {
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, TODAY.minusDays(29), TODAY))
                .thenReturn(List.of());

        String result = santeTools.getTendanceSante(MEMORY_ID, null);

        assertThat(result).contains("Aucune donnée santé");
    }

    @Test
    void getChargeCardioHebdo_semaineAvecCible_comparesToTarget() {
        SanteCardioHebdoDto semaine = new SanteCardioHebdoDto(TODAY.minusDays(6), 300.0, 200.0, 280.0, 90);
        when(santeQueryService.getCardioHebdo(user, 1)).thenReturn(List.of(semaine));

        String result = santeTools.getChargeCardioHebdo(MEMORY_ID);

        assertThat(result).contains("300").contains("au-dessus de la cible");
    }

    @Test
    void getChargeCardioHebdo_aucuneCharge_saysNoLoad() {
        when(santeQueryService.getCardioHebdo(user, 1)).thenReturn(List.of());

        String result = santeTools.getChargeCardioHebdo(MEMORY_ID);

        assertThat(result).contains("Aucune charge cardio");
    }

    @Test
    void getFrequenceCardiaqueDuJour_donneesPresentes_describesZones() {
        SanteJour jour = SanteJour.builder().utilisateur(user).date(TODAY).fcMin(55).fcMoyenne(78.0).fcMax(150)
                .minutesZoneBruleuse(20).minutesZoneCardio(10).minutesZonePic(2).build();
        when(santeJourRepository.findByUtilisateurAndDate(user, TODAY)).thenReturn(Optional.of(jour));

        String result = santeTools.getFrequenceCardiaqueDuJour(MEMORY_ID, null);

        assertThat(result).contains("min 55").contains("max 150").contains("pic 2 min");
    }

    @Test
    void getFrequenceCardiaqueDuJour_aucuneDonnee_saysNoData() {
        when(santeJourRepository.findByUtilisateurAndDate(user, TODAY)).thenReturn(Optional.empty());

        String result = santeTools.getFrequenceCardiaqueDuJour(MEMORY_ID, null);

        assertThat(result).contains("Aucune donnée de fréquence cardiaque");
    }

    @Test
    void getEtatSync_typeEnEchec_listsIt() {
        SanteSyncState etat = SanteSyncState.builder().utilisateur(user).typeDonnee("SLEEP")
                .dernierStatut(StatutSync.INDISPONIBLE).build();
        when(santeSyncStateRepository.findByUtilisateur(user)).thenReturn(List.of(etat));

        String result = santeTools.getEtatSync(MEMORY_ID);

        assertThat(result).contains("SLEEP").contains("INDISPONIBLE");
    }

    @Test
    void getEtatSync_toutEstOk_saysUpToDate() {
        SanteSyncState etat = SanteSyncState.builder().utilisateur(user).typeDonnee("STEPS")
                .dernierStatut(StatutSync.OK).build();
        when(santeSyncStateRepository.findByUtilisateur(user)).thenReturn(List.of(etat));

        String result = santeTools.getEtatSync(MEMORY_ID);

        assertThat(result).contains("à jour");
    }

    private List<SanteJour> fabriquerHistoriqueVfc() {
        List<SanteJour> jours = new java.util.ArrayList<>();
        double[] vfcValeurs = {60, 62, 61, 63, 59, 64, 60, 62, 61, 63};
        for (int i = 0; i < vfcValeurs.length; i++) {
            LocalDate d = TODAY.minusDays(vfcValeurs.length - i);
            jours.add(SanteJour.builder().utilisateur(user).date(d).vfcMs(vfcValeurs[i]).build());
        }
        jours.add(SanteJour.builder().utilisateur(user).date(TODAY).vfcMs(90.0).build());
        return jours;
    }
}
