package com.kronos.chiron.exportation.service.classeur;

import com.kronos.chiron.exportation.service.classeur.DonneesExport.DegressifDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ExerciceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.GenreExercice;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SerieDonnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClasseurBuilderTest {

    private static final LocalDateTime GENERE = LocalDateTime.of(2026, 11, 2, 18, 30);

    private ClasseurBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ClasseurBuilder();
    }

    private static SerieDonnees serie(double poids, int reps) {
        return new SerieDonnees(poids, reps, null, null, List.of());
    }

    private static ExerciceDonnees muscu(String cle, String nom, String commentaire, SerieDonnees... series) {
        return new ExerciceDonnees(cle, nom, commentaire, GenreExercice.MUSCU, List.of(series));
    }

    private static SeanceDonnees seance(long id, LocalDate jour, String note, ExerciceDonnees... exercices) {
        return new SeanceDonnees(id, "Push A", jour.atTime(18, 0), note, List.of(exercices));
    }

    private DonneesExport pushA() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1", "d2"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), "Bonne forme",
                        muscu("d1", "Développé couché", "Épaule gauche", serie(100, 8), serie(100, 8), serie(95, 10)),
                        muscu("d2", "Tractions", null, serie(0, 10))),
                seance(2, LocalDate.of(2026, 10, 12), null,
                        muscu("d1", "Développé couché", null, serie(105, 6), serie(100, 8)),
                        muscu("d2", "Tractions", "Lestées", serie(5, 8))),
                seance(3, LocalDate.of(2026, 10, 19), null,
                        muscu("d1", "Développé couché", null, serie(110, 5)))));
        return new DonneesExport("Programme force", GENERE, List.of(modele));
    }

    private List<Onglet> onglets(DonneesExport donnees) {
        return builder.onglets(donnees);
    }

    private Onglet onglet(DonneesExport donnees, String titre) {
        return onglets(donnees).stream().filter(o -> o.titre.equals(titre)).findFirst().orElseThrow();
    }

    private String texte(Onglet onglet, int ligne, int colonne) {
        Object valeur = onglet.lignes.get(ligne).get(colonne).valeur();
        return valeur instanceof String texte ? texte : "";
    }

    private Double nombre(Onglet onglet, int ligne, int colonne) {
        Object valeur = onglet.lignes.get(ligne).get(colonne).valeur();
        return valeur instanceof Double nombre ? nombre : null;
    }

    private Onglet seance(ModeleDonnees modele) {
        return onglet(new DonneesExport("E", GENERE, List.of(modele)), "Push A");
    }

    // --- structure du classeur ---

    @Test
    void onglets_creeLesTroisFamillesDOngletsDansLOrdre() {
        List<Onglet> onglets = onglets(pushA());

        assertThat(onglets).extracting(o -> o.titre).containsExactly("Synthèse", "Push A", "Historique");
    }

    @Test
    void onglets_ongletDeSeance_figeLesEntetesEtLesColonnesDExercice() {
        Onglet onglet = onglet(pushA(), "Push A");

        assertThat(onglet.lignesFigees).isEqualTo(5);
        assertThat(onglet.colonnesFigees).isEqualTo(2);
        assertThat(onglet.colonnesGrille).isGreaterThanOrEqualTo(14);
    }

    @Test
    void onglets_bandeau_diraQueLeFichierEstGenereEtNonMisAJourSeul() {
        Onglet onglet = onglet(pushA(), "Push A");

        assertThat(texte(onglet, 0, 0)).isEqualTo("Push A");
        assertThat(texte(onglet, 1, 0)).isEqualTo(
                "3 séances · du 05/10/2026 au 19/10/2026 · généré le 02/11/2026 à 18:30");
        assertThat(texte(onglet, 2, 0)).contains("Fichier généré par Chiron");
        assertThat(onglet.fusions).contains(new Plage(0, 1, 0, 6), new Plage(1, 2, 0, 6), new Plage(2, 3, 0, 6));
    }

    @Test
    void onglets_blocChargeMax_donneLaChargeMaxDeChaqueExerciceParSeance() {
        Onglet onglet = onglet(pushA(), "Push A");

        assertThat(texte(onglet, 3, 0)).isEqualTo("CHARGE MAX");
        assertThat(texte(onglet, 3, 2)).isEqualTo("05/10");
        assertThat(texte(onglet, 3, 3)).isEqualTo("12/10");
        assertThat(texte(onglet, 3, 4)).isEqualTo("19/10");
        assertThat(texte(onglet, 5, 0)).isEqualTo("Développé couché");
        assertThat(texte(onglet, 5, 1)).isEqualTo("kg");
        assertThat(nombre(onglet, 5, 2)).isEqualTo(100.0);
        assertThat(nombre(onglet, 5, 3)).isEqualTo(105.0);
        assertThat(nombre(onglet, 5, 4)).isEqualTo(110.0);
        assertThat(texte(onglet, 6, 0)).isEqualTo("Tractions");
        assertThat(nombre(onglet, 6, 2)).isEqualTo(0.0);
        assertThat(nombre(onglet, 6, 3)).isEqualTo(5.0);
        assertThat(nombre(onglet, 6, 4)).isNull();
    }

    @Test
    void onglets_blocSeries_detailleToutesLesSeriesEtLesDegressifs() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, new ExerciceDonnees("d1", "Développé couché", null,
                        GenreExercice.MUSCU, List.of(serie(100, 8),
                                new SerieDonnees(90, 12, null, null, List.of(new DegressifDonnees(70, 8))),
                                serie(32.5, 10))))));

        Onglet onglet = seance(modele);

        assertThat(texte(onglet, 7, 0)).isEqualTo("SÉRIES");
        assertThat(texte(onglet, 8, 2)).isEqualTo("100×8 · 90×12 › 70×8 · 32,5×10");
    }

    @Test
    void onglets_poidsDuCorpsSeuls_sontGradesEnRepetitions() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d2"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d2", "Tractions", null, serie(0, 8), serie(0, 12))),
                seance(2, LocalDate.of(2026, 10, 12), null, muscu("d2", "Tractions", null, serie(0, 14)))));

        Onglet onglet = seance(modele);

        assertThat(texte(onglet, 5, 1)).isEqualTo("reps");
        assertThat(nombre(onglet, 5, 2)).isEqualTo(12.0);
        assertThat(nombre(onglet, 5, 3)).isEqualTo(14.0);
        assertThat(texte(onglet, 8, 2)).isEqualTo("PDC×8 · PDC×12");
    }

    @Test
    void onglets_blocCommentaires_reprendLeCommentaireDeLExerciceEtLaNoteDeSeance() {
        Onglet onglet = onglet(pushA(), "Push A");

        assertThat(texte(onglet, 12, 0)).isEqualTo("COMMENTAIRES");
        assertThat(texte(onglet, 13, 0)).isEqualTo("Développé couché");
        assertThat(texte(onglet, 13, 2)).isEqualTo("Épaule gauche");
        assertThat(texte(onglet, 14, 3)).isEqualTo("Lestées");
        assertThat(texte(onglet, 15, 0)).isEqualTo("Note de séance");
        assertThat(texte(onglet, 15, 2)).isEqualTo("Bonne forme");
    }

    @Test
    void onglets_ajouteUnGraphiqueParExerciceAlimenteParSaLigne() {
        Onglet onglet = onglet(pushA(), "Push A");

        assertThat(onglet.graphiques).hasSize(2);
        GraphiqueProgression premier = onglet.graphiques.get(0);
        assertThat(premier.titre()).isEqualTo("Développé couché (kg)");
        assertThat(premier.unite()).isEqualTo("kg");
        assertThat(premier.ligneDonnees()).isEqualTo(5);
        assertThat(premier.ligneDates()).isEqualTo(3);
        assertThat(premier.nbSeances()).isEqualTo(3);
        assertThat(premier.ancreColonne()).isEqualTo(2);
        GraphiqueProgression second = onglet.graphiques.get(1);
        assertThat(second.titre()).isEqualTo("Tractions (kg)");
        assertThat(second.ancreColonne()).isEqualTo(7);
        assertThat(second.ancreLigne()).isEqualTo(premier.ancreLigne());
    }

    @Test
    void onglets_troisiemeGraphique_passeALaBandeSuivante() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("a", "b", "c"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("a", "A", null, serie(10, 5)),
                        muscu("b", "B", null, serie(20, 5)), muscu("c", "C", null, serie(30, 5)))));

        Onglet onglet = seance(modele);

        assertThat(onglet.graphiques).hasSize(3);
        assertThat(onglet.graphiques.get(2).ancreColonne()).isEqualTo(2);
        assertThat(onglet.graphiques.get(2).ancreLigne()).isGreaterThan(onglet.graphiques.get(0).ancreLigne());
    }

    // --- séances et exercices particuliers ---

    @Test
    void onglets_modeleSansSeance_afficheUnMessageEtAucunGraphique() {
        ModeleDonnees vide = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of());

        Onglet onglet = seance(vide);

        assertThat(texte(onglet, 5, 0)).contains("Aucune séance enregistrée");
        assertThat(onglet.graphiques).isEmpty();
    }

    @Test
    void onglets_cardioEtWod_ontLeurPropreUnite() {
        ExerciceDonnees course = new ExerciceDonnees("c", "Course", null, GenreExercice.CARDIO,
                List.of(new SerieDonnees(0, 0, 52.0, 10500.0, List.of())));
        ExerciceDonnees cindy = new ExerciceDonnees("w", "Cindy", null, GenreExercice.WOD,
                List.of(new SerieDonnees(0, 18, 20.0, null, List.of())));
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("c", "w"),
                List.of(seance(1, LocalDate.of(2026, 10, 5), null, course, cindy)));

        Onglet onglet = seance(modele);

        assertThat(texte(onglet, 5, 1)).isEqualTo("km");
        assertThat(nombre(onglet, 5, 2)).isEqualTo(10.5);
        assertThat(texte(onglet, 6, 1)).isEqualTo("tours");
        assertThat(nombre(onglet, 6, 2)).isEqualTo(18.0);
        assertThat(texte(onglet, 9, 2)).isEqualTo("10,5 km · 52 min");
        assertThat(texte(onglet, 10, 2)).isEqualTo("18 tours");
    }

    @Test
    void onglets_exerciceAjouteHorsModele_apparaitApresCeuxDuModele() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d1", "Développé couché", null, serie(100, 8)),
                        muscu("x", "Dips", null, serie(20, 10)))));

        Onglet onglet = seance(modele);

        assertThat(texte(onglet, 5, 0)).isEqualTo("Développé couché");
        assertThat(texte(onglet, 6, 0)).isEqualTo("Dips");
    }

    @Test
    void onglets_exerciceFaitDeuxFoisDansLaSeance_fusionneSesSeries() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d1", "Développé couché", "matin", serie(100, 8)),
                        muscu("d1", "Développé couché", "soir", serie(105, 5)))));

        Onglet onglet = seance(modele);

        assertThat(nombre(onglet, 5, 2)).isEqualTo(105.0);
        assertThat(texte(onglet, 8, 2)).isEqualTo("100×8 · 105×5");
        assertThat(texte(onglet, 11, 2)).isEqualTo("matin / soir");
    }

    @Test
    void onglets_seancesDeDeuxAnnees_precisentLAnneeDansLesDates() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2025, 12, 29), null, muscu("d1", "Développé couché", null, serie(100, 8))),
                seance(2, LocalDate.of(2026, 1, 5), null, muscu("d1", "Développé couché", null, serie(105, 8)))));

        Onglet onglet = seance(modele);

        assertThat(texte(onglet, 3, 2)).isEqualTo("29/12/25");
        assertThat(texte(onglet, 3, 3)).isEqualTo("05/01/26");
    }

    // --- Synthèse et Historique ---

    @Test
    void onglets_synthese_donnePremiereDerniereEtRecord() {
        Onglet onglet = onglet(pushA(), "Synthèse");

        assertThat(texte(onglet, 3, 0)).isEqualTo("Séance");
        assertThat(texte(onglet, 4, 0)).isEqualTo("Push A");
        assertThat(texte(onglet, 4, 1)).isEqualTo("Développé couché");
        assertThat(nombre(onglet, 4, 3)).isEqualTo(100.0);
        assertThat(nombre(onglet, 4, 4)).isEqualTo(110.0);
        assertThat(nombre(onglet, 4, 5)).isEqualTo(110.0);
        assertThat(texte(onglet, 4, 6)).isEqualTo("19/10/2026");
        assertThat(nombre(onglet, 4, 7)).isEqualTo(3.0);
    }

    @Test
    void onglets_synthese_signaleUnModeleSansSeance() {
        ModeleDonnees vide = new ModeleDonnees(5L, "Jambes", List.of("d1"), List.of());

        Onglet onglet = onglet(new DonneesExport("E", GENERE, List.of(vide)), "Synthèse");

        assertThat(texte(onglet, 4, 0)).isEqualTo("Jambes");
        assertThat(texte(onglet, 4, 1)).isEqualTo("Aucune séance enregistrée");
    }

    @Test
    void onglets_historique_listeLesPlusRecentesEnPremierAvecUneVraieDate() {
        Onglet onglet = onglet(pushA(), "Historique");

        double serial = ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(2026, 10, 19));
        assertThat(nombre(onglet, 1, 0)).isEqualTo(serial);
        assertThat(onglet.lignes.get(1).get(0).style().formatNombre()).isEqualTo("dd/mm/yyyy");
        assertThat(texte(onglet, 1, 3)).isEqualTo("Développé couché");
        assertThat(texte(onglet, 0, 7)).isEqualTo("Commentaire");
        assertThat(onglet.filtre).isEqualTo(new Plage(0, 6, 0, 9));
        assertThat(onglet.lignes).hasSize(1 + 5);
    }

    @Test
    void onglets_historique_neRepeteLaNoteQueSurLaPremiereLigneDeLaSeance() {
        Onglet onglet = onglet(pushA(), "Historique");

        List<String> notes = new ArrayList<>();
        for (int ligne = 1; ligne < onglet.lignes.size(); ligne++) {
            String note = texte(onglet, ligne, 8);
            if (!note.isEmpty()) notes.add(note);
        }
        assertThat(notes).containsExactly("Bonne forme");
    }

    @Test
    void onglets_historiqueSansSeance_gardeUnFiltreSurLEntete() {
        Onglet onglet = onglet(new DonneesExport("E", GENERE, List.of()), "Historique");

        assertThat(onglet.lignes).hasSize(1);
        assertThat(onglet.filtre).isEqualTo(new Plage(0, 2, 0, 9));
    }

    // --- noms d'onglets ---

    @Test
    void nomUnique_retireLesCaracteresInterditsEtTronqueALaLimiteDExcel() {
        Set<String> pris = new HashSet<>();

        assertThat(ClasseurBuilder.nomUnique("Push/Pull: [A]*?", pris)).isEqualTo("Push Pull A");
        assertThat(ClasseurBuilder.nomUnique("x".repeat(150), pris)).hasSize(31);
        assertThat(ClasseurBuilder.nomUnique("   ", pris)).isEqualTo("Séance");
        assertThat(ClasseurBuilder.nomUnique(null, pris)).isEqualTo("Séance (2)");
    }

    @Test
    void nomUnique_neCommenceNiNeFinitParUneApostrophe() {
        assertThat(ClasseurBuilder.nomUnique("'Push A'", new HashSet<>())).isEqualTo("Push A");
    }

    @Test
    void nomUnique_distingueLesDoublonsSansTenirCompteDeLaCasse() {
        Set<String> pris = new HashSet<>(Set.of("synthèse"));

        assertThat(ClasseurBuilder.nomUnique("Synthèse", pris)).isEqualTo("Synthèse (2)");
        assertThat(ClasseurBuilder.nomUnique("SYNTHÈSE", pris)).isEqualTo("SYNTHÈSE (3)");
    }

    @Test
    void nomUnique_unDoublonTropLongResteDansLaLimite() {
        Set<String> pris = new HashSet<>();
        String titre = "y".repeat(40);

        String premier = ClasseurBuilder.nomUnique(titre, pris);
        String second = ClasseurBuilder.nomUnique(titre, pris);

        assertThat(premier).hasSize(31);
        assertThat(second).hasSize(31).endsWith(" (2)").isNotEqualTo(premier);
    }

    @Test
    void onglets_deuxModelesDeMemeTitre_ontDesOngletsDistincts() {
        ModeleDonnees a = new ModeleDonnees(5L, "Push", List.of(), List.of());
        ModeleDonnees b = new ModeleDonnees(6L, "Push", List.of(), List.of());

        List<Onglet> onglets = onglets(new DonneesExport("E", GENERE, List.of(a, b)));

        assertThat(onglets).extracting(o -> o.titre).containsExactly("Synthèse", "Push", "Push (2)", "Historique");
    }

    @Test
    void onglets_modeleIntituleSynthese_neEntrePasEnCollisionAvecLOngletReserve() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Synthèse", List.of(), List.of());

        List<Onglet> onglets = onglets(new DonneesExport("E", GENERE, List.of(modele)));

        assertThat(onglets).extracting(o -> o.titre).containsExactly("Synthèse", "Synthèse (2)", "Historique");
    }
}
