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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportMarkdownTest {

    private static final LocalDateTime GENERE = LocalDateTime.of(2026, 11, 2, 18, 30);

    private ExportMarkdown exportMarkdown;

    @BeforeEach
    void setUp() {
        exportMarkdown = new ExportMarkdown();
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

    private static DonneesExport export(ModeleDonnees... modeles) {
        return new DonneesExport("Programme force", GENERE, List.of(modeles));
    }

    private DonneesExport pushA() {
        return export(new ModeleDonnees(5L, "Push A", List.of("d1", "d2"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), "Bonne forme",
                        muscu("d1", "Développé couché", "Épaule gauche", serie(100, 8), serie(95, 10)),
                        muscu("d2", "Tractions", null, serie(0, 10))),
                seance(2, LocalDate.of(2026, 10, 12), null,
                        muscu("d1", "Développé couché", null, serie(105, 6)),
                        muscu("d2", "Tractions", "Lestées", serie(5, 8))),
                seance(3, LocalDate.of(2026, 10, 19), null,
                        muscu("d1", "Développé couché", null, serie(110, 5))))));
    }

    @Test
    void generer_produitLeTexteCompletAttendu() {
        String attendu = """
                # Programme force — export du 02/11/2026
                Données d'entraînement, du plus ancien au plus récent. Unités : kg, reps, km, min, tours.
                PDC = poids du corps. A×B = charge × répétitions. › = série dégressive.
                max = meilleure charge de la séance (reps max au poids du corps, distance ou durée en cardio).

                ## Push A — 3 séances, 05/10/2026 → 19/10/2026
                ### Développé couché [kg] — 3 séances, 100 → 110, record 110 (19/10)
                05/10  100×8 · 95×10  max 100  « Épaule gauche »
                12/10  105×6  max 105
                19/10  110×5  max 110
                ### Tractions [kg] — 2 séances, 0 → 5, record 5 (12/10)
                05/10  PDC×10  max 0
                12/10  5×8  max 5  « Lestées »
                Notes : 05/10 « Bonne forme »
                """;

        assertThat(exportMarkdown.generer(pushA())).isEqualTo(attendu);
    }

    @Test
    void generer_ecritLesDecimalesAvecUnPointEtChaineLesDegressifs() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, new ExerciceDonnees("d1", "Développé couché", null,
                        GenreExercice.MUSCU, List.of(serie(100, 8),
                                new SerieDonnees(90, 12, null, null, List.of(new DegressifDonnees(70, 8))),
                                serie(32.5, 10))))));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("05/10  100×8 · 90×12 › 70×8 · 32.5×10  max 100");
        assertThat(texte).doesNotContain("32,5");
    }

    @Test
    void generer_graduePoidsDuCorpsEnRepetitions() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Pull", List.of("d2"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d2", "Tractions", null, serie(0, 8), serie(0, 12))),
                seance(2, LocalDate.of(2026, 10, 12), null, muscu("d2", "Tractions", null, serie(0, 14)))));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("### Tractions [reps] — 2 séances, 12 → 14, record 14 (12/10)");
        assertThat(texte).contains("05/10  PDC×8 · PDC×12  max 12");
    }

    @Test
    void generer_decritLeCardioEtLesWodAvecLeurUnite() {
        ExerciceDonnees course = new ExerciceDonnees("c", "Course", null, GenreExercice.CARDIO,
                List.of(new SerieDonnees(0, 0, 52.0, 10500.0, List.of()),
                        new SerieDonnees(0, 0, 15.0, 3000.0, List.of())));
        ExerciceDonnees cindy = new ExerciceDonnees("w", "Cindy", null, GenreExercice.WOD,
                List.of(new SerieDonnees(0, 18, 20.0, null, List.of())));
        ModeleDonnees modele = new ModeleDonnees(5L, "Mixte", List.of("c", "w"),
                List.of(seance(1, LocalDate.of(2026, 10, 5), null, course, cindy)));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("### Course [km] — 1 séance");
        assertThat(texte).contains("05/10  10.5 km · 52 min ; 3 km · 15 min  max 13.5");
        assertThat(texte).contains("### Cindy [tours] — 1 séance");
        assertThat(texte).contains("05/10  18 tours  max 18");
        assertThat(texte).doesNotContain("|");
    }

    @Test
    void generer_neDecritPasUnExerciceAbsentDUneSeance() {
        String texte = exportMarkdown.generer(pushA());

        assertThat(texte).doesNotContain("19/10  PDC");
        assertThat(texte.lines().filter(ligne -> ligne.startsWith("19/10")).count()).isEqualTo(1);
    }

    @Test
    void generer_unExerciceFaitUneSeuleFois_neDonnePasDeTendance() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d1", "Dips", null, serie(20, 10)))));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("### Dips [kg] — 1 séance\n");
        assertThat(texte).doesNotContain("record");
    }

    @Test
    void generer_unRecordEgalSeDateALaPremiereFois() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d1", "Squat", null, serie(100, 5))),
                seance(2, LocalDate.of(2026, 10, 12), null, muscu("d1", "Squat", null, serie(110, 5))),
                seance(3, LocalDate.of(2026, 10, 19), null, muscu("d1", "Squat", null, serie(110, 3)))));

        assertThat(exportMarkdown.generer(export(modele))).contains("record 110 (12/10)");
    }

    @Test
    void generer_modeleSansSeance_ledit() {
        ModeleDonnees vide = new ModeleDonnees(6L, "Jambes", List.of("d1"), List.of());

        String texte = exportMarkdown.generer(export(vide));

        assertThat(texte).contains("## Jambes — aucune séance enregistrée\n");
        assertThat(texte).doesNotContain("###");
    }

    @Test
    void generer_surPlusieursAnnees_precisLAnneeDansChaqueDate() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2025, 12, 29), null, muscu("d1", "Squat", null, serie(100, 5))),
                seance(2, LocalDate.of(2026, 1, 5), null, muscu("d1", "Squat", null, serie(105, 5)))));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("29/12/25  100×5  max 100").contains("05/01/26  105×5  max 105");
        assertThat(texte).contains("— 2 séances, 29/12/2025 → 05/01/2026");
    }

    @Test
    void generer_ramenePlusieursLignesDeCommentaireEtDeNoteASeule() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), "Fatigué\n  mais  content\n", muscu("d1", "Squat",
                        "épaule\r\n  douloureuse", serie(100, 5)))));

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte).contains("« épaule douloureuse »");
        assertThat(texte).contains("Notes : 05/10 « Fatigué mais content »");
    }

    @Test
    void generer_ignoreLesNotesVides() {
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), "   ", muscu("d1", "Squat", null, serie(100, 5)))));

        assertThat(exportMarkdown.generer(export(modele))).doesNotContain("Notes");
    }

    @Test
    void generer_listeLesModelesDansLOrdreEtSepareLesSections() {
        ModeleDonnees push = new ModeleDonnees(5L, "Push", List.of("d1"), List.of(
                seance(1, LocalDate.of(2026, 10, 5), null, muscu("d1", "Squat", null, serie(100, 5)))));
        ModeleDonnees pull = new ModeleDonnees(6L, "Pull", List.of("d2"), List.of());

        String texte = exportMarkdown.generer(export(push, pull));

        assertThat(texte).containsSubsequence("## Push —", "## Pull —");
        assertThat(texte).contains("\n\n## Push").contains("\n\n## Pull");
    }

    @Test
    void generer_resteCompactSurUnLongHistorique() {
        List<SeanceDonnees> seances = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            List<ExerciceDonnees> exercices = new ArrayList<>();
            for (int e = 0; e < 6; e++) {
                exercices.add(muscu("d" + e, "Exercice numéro " + e, null, serie(60 + i * 0.5, 8),
                        serie(57.5 + i * 0.5, 10), serie(55 + i * 0.5, 12)));
            }
            seances.add(new SeanceDonnees((long) i, "Push A", LocalDate.of(2026, 1, 1).plusDays(i).atTime(18, 0),
                    null, exercices));
        }
        ModeleDonnees modele = new ModeleDonnees(5L, "Push A", List.of("d0", "d1", "d2", "d3", "d4", "d5"), seances);

        String texte = exportMarkdown.generer(export(modele));

        assertThat(texte.length()).isLessThan(40_000);
        assertThat(texte).doesNotContain("|");
    }
}
