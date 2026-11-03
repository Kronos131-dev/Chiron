package com.kronos.chiron.exportation.service;

import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.seance.model.CardioType;
import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.model.WodType;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.exportation.model.ExportProgramme;
import com.kronos.chiron.exportation.model.ExportProgrammeExclusion;
import com.kronos.chiron.exportation.model.ExportProgrammeModele;
import com.kronos.chiron.exportation.model.ExportProgrammeSeance;
import com.kronos.chiron.exportation.service.classeur.DonneesExport;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.GenreExercice;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeurDonneesExportTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SeanceRepository seanceRepository;
    @Mock
    private RattachementParTitre rattachementParTitre;

    private ChargeurDonneesExport chargeur;
    private Utilisateur alice;
    private ExportProgramme export;

    @BeforeEach
    void setUp() {
        chargeur = new ChargeurDonneesExport(seanceRepository, rattachementParTitre, CLOCK);
        alice = Utilisateur.builder().id(1L).username("alice").build();
        export = ExportProgramme.builder().id(9L).utilisateur(alice).nom("Programme").build();
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of());
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of());
        when(rattachementParTitre.chercher(eq(1L), anyMap())).thenReturn(Map.of());
    }

    private ExerciceDefinition definition(long id, CardioType cardio, WodType wod) {
        return ExerciceDefinition.builder().id(id).cardioType(cardio).wodType(wod)
                .musclesSecondaires(new ArrayList<>()).build();
    }

    private Exercice exercice(String nom, int ordre, ExerciceDefinition definition, Serie... series) {
        Exercice exercice = new Exercice();
        exercice.setNom(nom);
        exercice.setDisplayOrder(ordre);
        exercice.setDefinition(definition);
        exercice.setCommentaire("note " + nom);
        for (Serie serie : series)
            exercice.addSerie(serie);
        return exercice;
    }

    private Serie serie(double poids, int reps) {
        Serie serie = new Serie();
        serie.setPoids(poids);
        serie.setNombreReps(reps);
        return serie;
    }

    private Seance seance(long id, boolean historique, Long modeleId, int jour, Exercice... exercices) {
        Seance seance = new Seance();
        seance.setId(id);
        seance.setTitre("Séance " + id);
        seance.setHistorique(historique);
        seance.setModeleId(modeleId);
        seance.setStartTime(LocalDateTime.of(2026, 10, jour, 18, 0));
        seance.setEndTime(LocalDateTime.of(2026, 10, jour, 19, 0));
        seance.setUtilisateur(alice);
        for (Exercice exercice : exercices)
            seance.addExercice(exercice);
        return seance;
    }

    private void lierModele(long modeleId, int ordre) {
        export.ajouterModele(ExportProgrammeModele.builder().modeleId(modeleId).ordre(ordre).build());
    }

    private void ajouter(long seanceId, long modeleId) {
        export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(seanceId).modeleId(modeleId).build());
    }

    @Test
    void charger_assembleLesSeancesParModeleDansLOrdreChronologique() {
        lierModele(5L, 0);
        Seance modele = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)),
                exercice("Dips", 1, definition(2, null, null)));
        Seance recente = seance(21L, true, 5L, 20, exercice("Squat", 0, definition(1, null, null), serie(100, 5)));
        Seance ancienne = seance(22L, true, 5L, 6, exercice("Squat", 0, definition(1, null, null), serie(90, 5)));
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(modele));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(recente, ancienne));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.nom()).isEqualTo("Programme");
        assertThat(donnees.genereLe()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0));
        ModeleDonnees onglet = donnees.modeles().get(0);
        assertThat(onglet.titre()).isEqualTo("Séance 5");
        assertThat(onglet.ordreExercices()).containsExactly("d1", "d2");
        assertThat(onglet.seances()).extracting(s -> s.id()).containsExactly(22L, 21L);
    }

    @Test
    void charger_uneSeanceAjouteeALaMainVaDansLOngletChoisi() {
        lierModele(5L, 0);
        lierModele(6L, 1);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance m6 = seance(6L, false, null, 1, exercice("Rowing", 0, definition(3, null, null)));
        Seance passee = seance(30L, true, null, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        ajouter(30L, 6L);
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5, m6));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(passee));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).isEmpty();
        assertThat(donnees.modeles().get(1).seances()).extracting(s -> s.id()).containsExactly(30L);
    }

    @Test
    void charger_leChoixManuelPrimeSurLeModeleDOrigine() {
        lierModele(5L, 0);
        lierModele(6L, 1);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance m6 = seance(6L, false, null, 1, exercice("Rowing", 0, definition(3, null, null)));
        Seance faite = seance(31L, true, 5L, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        ajouter(31L, 6L);
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5, m6));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(faite));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(faite));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).isEmpty();
        assertThat(donnees.modeles().get(1).seances()).extracting(s -> s.id()).containsExactly(31L);
    }

    @Test
    void charger_uneSeanceSansLienRejointLOngletDeMemeTitre() {
        lierModele(5L, 0);
        lierModele(6L, 1);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance m6 = seance(6L, false, null, 1, exercice("Rowing", 0, definition(3, null, null)));
        Seance ancienne = seance(50L, true, null, 3, exercice("Rowing", 0, definition(3, null, null), serie(60, 8)));
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5, m6));
        when(rattachementParTitre.chercher(1L, Map.of(5L, "Séance 5", 6L, "Séance 6")))
                .thenReturn(Map.of(50L, 6L));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(ancienne));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).isEmpty();
        assertThat(donnees.modeles().get(1).seances()).extracting(s -> s.id()).containsExactly(50L);
    }

    @Test
    void charger_leChoixManuelPrimeSurLeRattachementParTitre() {
        lierModele(5L, 0);
        lierModele(6L, 1);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance m6 = seance(6L, false, null, 1, exercice("Rowing", 0, definition(3, null, null)));
        Seance faite = seance(51L, true, null, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        ajouter(51L, 5L);
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5, m6));
        when(rattachementParTitre.chercher(eq(1L), anyMap())).thenReturn(Map.of(51L, 6L));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(faite));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).extracting(s -> s.id()).containsExactly(51L);
        assertThat(donnees.modeles().get(1).seances()).isEmpty();
    }

    @Test
    void charger_leModeleDOrigineNEstPasRechercheUneSecondeFoisParTitre() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance faite = seance(52L, true, 5L, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(faite));
        when(rattachementParTitre.chercher(eq(1L), anyMap())).thenReturn(Map.of(52L, 5L));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).extracting(s -> s.id()).containsExactly(52L);
        verify(seanceRepository, never()).findCompletesDeLUtilisateur(eq(1L), anyCollection());
    }

    @Test
    void charger_ecarteUneSeanceExclueMemeSiSonModeleOuSonTitreCorrespond() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance parLien = seance(60L, true, 5L, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        Seance parTitre = seance(61L, true, null, 4, exercice("Squat", 0, definition(1, null, null), serie(85, 8)));
        Seance gardee = seance(62L, true, 5L, 5, exercice("Squat", 0, definition(1, null, null), serie(90, 8)));
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(60L).build());
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(61L).build());
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(parLien, gardee));
        when(rattachementParTitre.chercher(eq(1L), anyMap())).thenReturn(Map.of(61L, 5L));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(parTitre));

        DonneesExport donnees = chargeur.charger(export);

        assertThat(donnees.modeles().get(0).seances()).extracting(s -> s.id()).containsExactly(62L);
    }

    @Test
    void charger_uneSeanceAjouteeALaMainResteMemeSiUneExclusionLaVise() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance ajoutee = seance(63L, true, null, 3, exercice("Squat", 0, definition(1, null, null), serie(80, 8)));
        ajouter(63L, 5L);
        export.ajouterExclusion(ExportProgrammeExclusion.builder().seanceId(63L).build());
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesDeLUtilisateur(eq(1L), anyCollection())).thenReturn(List.of(ajoutee));

        assertThat(chargeur.charger(export).modeles().get(0).seances()).extracting(s -> s.id())
                .containsExactly(63L);
    }

    @Test
    void charger_neGardeQueLesSeancesQuiOntDesExercices() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1, exercice("Squat", 0, definition(1, null, null)));
        Seance vide = seance(40L, true, 5L, 4);
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(vide));

        assertThat(chargeur.charger(export).modeles().get(0).seances()).isEmpty();
    }

    @Test
    void charger_ignoreUnModeleSupprime() {
        lierModele(5L, 0);
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of());

        assertThat(chargeur.charger(export).modeles()).isEmpty();
    }

    @Test
    void charger_groupeLesExercicesParDefinitionSinonParNomNormalise() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1, exercice("Développé Couché", 0, null),
                exercice("Curl", 1, definition(7, null, null)));
        Seance faite = seance(41L, true, 5L, 4, exercice("developpe  couche", 0, null, serie(60, 8)),
                exercice("Curl marteau", 1, definition(7, null, null), serie(14, 10)));
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(faite));

        ModeleDonnees onglet = chargeur.charger(export).modeles().get(0);

        assertThat(onglet.ordreExercices()).containsExactly("ndeveloppe couche", "d7");
        assertThat(onglet.seances().get(0).exercices()).extracting(e -> e.cle())
                .containsExactly("ndeveloppe couche", "d7");
    }

    @Test
    void charger_reconnaitLeCardioEtLeWodAPartirDeLaDefinition() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1);
        Seance faite = seance(42L, true, 5L, 4,
                exercice("Course", 0, definition(8, CardioType.COURSE_EXTERIEUR, null), serie(0, 0)),
                exercice("Cindy", 1, definition(9, null, WodType.CINDY), serie(0, 18)),
                exercice("Rameur libre", 2, null, cardio()));
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(faite));

        List<GenreExercice> genres = chargeur.charger(export).modeles().get(0).seances().get(0).exercices()
                .stream().map(e -> e.genre()).toList();

        assertThat(genres).containsExactly(GenreExercice.CARDIO, GenreExercice.WOD, GenreExercice.CARDIO);
    }

    private Serie cardio() {
        Serie serie = new Serie();
        serie.setDureeMin(20.0);
        serie.setDistanceM(4000.0);
        return serie;
    }

    @Test
    void charger_recopieLesSeriesLesDegressifsEtLesCommentaires() {
        lierModele(5L, 0);
        Seance m5 = seance(5L, false, null, 1);
        Serie avecDegressif = serie(100, 8);
        Degressif degressif = new Degressif();
        degressif.setPoids(70);
        degressif.setNombreReps(10);
        avecDegressif.addDegressif(degressif);
        Seance faite = seance(43L, true, 5L, 4, exercice("Squat", 0, definition(1, null, null), avecDegressif));
        faite.setNote("Bonne séance");
        when(seanceRepository.findAllById(anyCollection())).thenReturn(List.of(m5));
        when(seanceRepository.findCompletesParModele(anyCollection())).thenReturn(List.of(faite));

        var seanceDonnees = chargeur.charger(export).modeles().get(0).seances().get(0);

        assertThat(seanceDonnees.note()).isEqualTo("Bonne séance");
        assertThat(seanceDonnees.exercices().get(0).commentaire()).isEqualTo("note Squat");
        var serieDonnees = seanceDonnees.exercices().get(0).series().get(0);
        assertThat(serieDonnees.poids()).isEqualTo(100);
        assertThat(serieDonnees.reps()).isEqualTo(8);
        assertThat(serieDonnees.degressifs()).extracting(d -> d.poids()).containsExactly(70.0);
    }
}
