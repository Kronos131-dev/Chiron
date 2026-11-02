package com.kronos.chiron.exportation.service.classeur;

import com.kronos.chiron.exportation.service.classeur.DonneesExport.ExerciceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.GenreExercice;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SerieDonnees;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xddf.usermodel.chart.XDDFChartAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ClasseurXlsxTest {

    private static final LocalDateTime GENERE = LocalDateTime.of(2026, 11, 2, 18, 30);

    private ClasseurXlsx classeurXlsx;

    @BeforeEach
    void setUp() {
        classeurXlsx = new ClasseurXlsx();
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
                        muscu("d1", "Développé couché", "Épaule gauche", serie(100, 8), serie(95, 10)),
                        muscu("d2", "Tractions", null, serie(0, 10))),
                seance(2, LocalDate.of(2026, 10, 12), null,
                        muscu("d1", "Développé couché", null, serie(105, 6)),
                        muscu("d2", "Tractions", "Lestées", serie(5, 8))),
                seance(3, LocalDate.of(2026, 10, 19), null,
                        muscu("d1", "Développé couché", null, serie(110, 5)))));
        return new DonneesExport("Programme force", GENERE, List.of(modele));
    }

    private XSSFWorkbook relire(byte[] contenu) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenu));
    }

    @Test
    void generer_produitUnClasseurXlsxQuiSeRouvre() throws IOException {
        byte[] contenu = classeurXlsx.generer(pushA());

        assertThat(contenu).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook classeur = relire(contenu)) {
            assertThat(classeur.getNumberOfSheets()).isEqualTo(3);
        }
    }

    @Test
    void generer_nommeLesOngletsDansLOrdre() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            assertThat(List.of(classeur.getSheetName(0), classeur.getSheetName(1), classeur.getSheetName(2)))
                    .containsExactly("Synthèse", "Push A", "Historique");
        }
    }

    @Test
    void generer_ecritLesValeursEtLesTextesDeLOngletDeSeance() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            XSSFSheet feuille = classeur.getSheet("Push A");

            assertThat(feuille.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Push A");
            assertThat(feuille.getRow(3).getCell(0).getStringCellValue()).isEqualTo("CHARGE MAX");
            assertThat(feuille.getRow(3).getCell(2).getStringCellValue()).isEqualTo("05/10");
            assertThat(feuille.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Développé couché");
            assertThat(feuille.getRow(5).getCell(2).getNumericCellValue()).isEqualTo(100.0);
            assertThat(feuille.getRow(5).getCell(4).getNumericCellValue()).isEqualTo(110.0);
            assertThat(feuille.getRow(6).getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(feuille.getRow(9).getCell(2).getStringCellValue()).isEqualTo("100×8 · 95×10");
            assertThat(feuille.getRow(13).getCell(2).getStringCellValue()).isEqualTo("Épaule gauche");
        }
    }

    @Test
    void generer_figeLesEntetesEtFusionneLeBandeau() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            XSSFSheet feuille = classeur.getSheet("Push A");

            assertThat(feuille.getPaneInformation().isFreezePane()).isTrue();
            assertThat((int) feuille.getPaneInformation().getHorizontalSplitPosition()).isEqualTo(5);
            assertThat((int) feuille.getPaneInformation().getVerticalSplitPosition()).isEqualTo(2);
            assertThat(feuille.getMergedRegions()).isNotEmpty();
            assertThat(feuille.getMergedRegions().get(0).formatAsString()).isEqualTo("A1:F1");
        }
    }

    @Test
    void generer_poseUnGraphiqueEnCourbeParExerciceSurSaLigne() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            List<XSSFChart> graphiques = classeur.getSheet("Push A").createDrawingPatriarch().getCharts();

            assertThat(graphiques).hasSize(2);
            assertThat(graphiques.get(0).getTitleText().getString()).isEqualTo("Développé couché (kg)");
            String xml = graphiques.get(0).getCTChart().xmlText();
            assertThat(xml).contains("lineChart").contains("'Push A'!$C$6:$E$6").contains("'Push A'!$C$4:$E$4");
            assertThat(graphiques.get(1).getCTChart().xmlText()).contains("'Push A'!$C$7:$E$7");
        }
    }

    @Test
    void generer_dessineUneCourbeFineEtNonUnAplatQuiRecouvreLeGraphique() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            String xml = classeur.getSheet("Push A").createDrawingPatriarch().getCharts().get(0).getCTChart()
                    .xmlText();

            Matcher epaisseur = Pattern.compile("<a:ln w=\"(\\d+)\"").matcher(xml);
            assertThat(epaisseur.find()).isTrue();
            assertThat(Long.parseLong(epaisseur.group(1))).isBetween(12_700L, 63_500L);
        }
    }

    @Test
    void generer_laisseUnTrouPourUneSeanceSansLExercice() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            String xml = classeur.getSheet("Push A").createDrawingPatriarch().getCharts().get(1).getCTChart()
                    .xmlText();

            assertThat(xml).contains("dispBlanksAs").contains("gap");
        }
    }

    @Test
    void generer_cadreLAxeVerticalSurLesValeursDeLExercice() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            List<XSSFChart> graphiques = classeur.getSheet("Push A").createDrawingPatriarch().getCharts();

            XDDFChartAxis axe = graphiques.get(0).getAxes().get(1);
            assertThat(axe.getMinimum()).isEqualTo(95.0);
            assertThat(axe.getMaximum()).isEqualTo(115.0);
        }
    }

    @Test
    void generer_laisseLesAutresOngletsSansGraphique() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            assertThat(classeur.getSheet("Synthèse").getDrawingPatriarch()).isNull();
            assertThat(classeur.getSheet("Historique").getDrawingPatriarch()).isNull();
        }
    }

    @Test
    void generer_historiqueEcritDesVraiesDatesEtUnFiltre() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            XSSFSheet feuille = classeur.getSheet("Historique");

            assertThat(DateUtil.isCellDateFormatted(feuille.getRow(1).getCell(0))).isTrue();
            assertThat(feuille.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 10, 19));
            assertThat(feuille.getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }

    @Test
    void generer_imprimeChaqueOngletEnPaysageSurUnePageDeLarge() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            for (int i = 0; i < classeur.getNumberOfSheets(); i++) {
                XSSFSheet feuille = classeur.getSheetAt(i);

                assertThat(feuille.getFitToPage()).isTrue();
                assertThat(feuille.getPrintSetup().getLandscape()).isTrue();
                assertThat((int) feuille.getPrintSetup().getFitWidth()).isEqualTo(1);
                assertThat((int) feuille.getPrintSetup().getFitHeight()).isZero();
            }
        }
    }

    @Test
    void generer_appliqueLesLargeursDeColonne() throws IOException {
        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(pushA()))) {
            XSSFSheet feuille = classeur.getSheet("Push A");

            assertThat(feuille.getColumnWidth(0)).isGreaterThan(feuille.getColumnWidth(1));
            assertThat(feuille.getColumnWidth(2)).isEqualTo(feuille.getColumnWidth(9));
        }
    }

    @Test
    void generer_exportSansAucuneSeance_produitQuandMemeUnClasseurValide() throws IOException {
        ModeleDonnees vide = new ModeleDonnees(5L, "Jambes", List.of("d1"), List.of());

        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(new DonneesExport("E", GENERE, List.of(vide))))) {
            assertThat(classeur.getNumberOfSheets()).isEqualTo(3);
            assertThat(classeur.getSheet("Jambes").getRow(5).getCell(0).getStringCellValue())
                    .contains("Aucune séance enregistrée");
        }
    }

    @Test
    void generer_desIntitulesLongsEtIdentiques_gardentDesNomsDOngletValidesPourExcel() throws IOException {
        List<ModeleDonnees> modeles = List.of(
                new ModeleDonnees(1L, "Push/Pull: très long intitulé de séance de force", List.of(), List.of()),
                new ModeleDonnees(2L, "Push/Pull: très long intitulé de séance de force", List.of(), List.of()));

        try (XSSFWorkbook classeur = relire(classeurXlsx.generer(new DonneesExport("E", GENERE, modeles)))) {
            for (int i = 0; i < classeur.getNumberOfSheets(); i++) {
                assertThat(classeur.getSheetName(i).length()).isLessThanOrEqualTo(31);
            }
            assertThat(classeur.getNumberOfSheets()).isEqualTo(4);
        }
    }
}
