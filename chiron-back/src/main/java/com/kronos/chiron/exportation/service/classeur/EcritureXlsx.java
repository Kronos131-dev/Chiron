package com.kronos.chiron.exportation.service.classeur;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EcritureXlsx {

    private static final double PIXELS_PAR_CARACTERE = 7.0;
    private static final int UNITES_PAR_CARACTERE = 256;
    private static final int LARGEUR_MAX = 255 * UNITES_PAR_CARACTERE;
    private static final int COLONNES_GRAPHIQUE = 4;
    private static final int LIGNES_GRAPHIQUE = 15;
    private static final double EPAISSEUR_COURBE_EMU = 38100;
    private static final String COULEUR_COURBE = "#c77a30";
    private static final String POLICE = "Calibri";

    private final XSSFWorkbook classeur = new XSSFWorkbook();
    private final Map<Cellule.Style, XSSFCellStyle> styles = new HashMap<>();

    static byte[] ecrire(List<Onglet> onglets) throws IOException {
        EcritureXlsx ecriture = new EcritureXlsx();
        try (XSSFWorkbook classeur = ecriture.classeur; ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            onglets.forEach(ecriture::ecrireOnglet);
            classeur.write(sortie);
            return sortie.toByteArray();
        }
    }

    private void ecrireOnglet(Onglet onglet) {
        XSSFSheet feuille = classeur.createSheet(onglet.titre);
        feuille.setTabColor(couleur(onglet.couleurOnglet));
        ecrireCellules(feuille, onglet);
        fixerLargeurs(feuille, onglet);
        if (onglet.lignesFigees > 0 || onglet.colonnesFigees > 0) {
            feuille.createFreezePane(onglet.colonnesFigees, onglet.lignesFigees);
        }
        for (Plage fusion : onglet.fusions) {
            if (fusion.ligneFin() - fusion.ligneDebut() > 1 || fusion.colonneFin() - fusion.colonneDebut() > 1) {
                feuille.addMergedRegion(adresse(fusion));
            }
        }
        if (onglet.filtre != null) {
            feuille.setAutoFilter(adresse(onglet.filtre));
        }
        if (!onglet.graphiques.isEmpty()) {
            XSSFDrawing dessin = feuille.createDrawingPatriarch();
            onglet.graphiques.forEach(graphique -> ecrireGraphique(feuille, dessin, graphique, onglet.colonnesFigees));
        }
    }

    private void ecrireCellules(XSSFSheet feuille, Onglet onglet) {
        for (int numero = 0; numero < onglet.lignes.size(); numero++) {
            XSSFRow ligne = feuille.createRow(numero);
            List<Cellule> cellules = onglet.lignes.get(numero);
            for (int colonne = 0; colonne < cellules.size(); colonne++) {
                Cellule cellule = cellules.get(colonne);
                XSSFCell cell = ligne.createCell(colonne);
                cell.setCellStyle(style(cellule.style()));
                if (cellule.valeur() instanceof String texte && !texte.isEmpty()) {
                    cell.setCellValue(texte);
                } else if (cellule.valeur() instanceof Double nombre) {
                    cell.setCellValue(nombre);
                }
            }
        }
    }

    private void fixerLargeurs(XSSFSheet feuille, Onglet onglet) {
        for (Map.Entry<Integer, Integer> largeur : onglet.largeurs.entrySet()) {
            int unites = unitesDeLargeur(largeur.getValue());
            if (largeur.getKey() == Onglet.DERNIERES_COLONNES) {
                for (int colonne = onglet.colonnesFigees; colonne < onglet.colonnesGrille; colonne++) {
                    feuille.setColumnWidth(colonne, unites);
                }
            } else {
                feuille.setColumnWidth(largeur.getKey(), unites);
            }
        }
    }

    private int unitesDeLargeur(int pixels) {
        return Math.min((int) Math.round(pixels / PIXELS_PAR_CARACTERE * UNITES_PAR_CARACTERE), LARGEUR_MAX);
    }

    private void ecrireGraphique(XSSFSheet feuille, XSSFDrawing dessin, GraphiqueProgression progression,
            int premiereColonne) {
        int derniereColonne = premiereColonne + progression.nbSeances() - 1;
        XSSFClientAnchor ancre = dessin.createAnchor(0, 0, 0, 0, progression.ancreColonne(), progression.ancreLigne(),
                progression.ancreColonne() + COLONNES_GRAPHIQUE, progression.ancreLigne() + LIGNES_GRAPHIQUE);
        XSSFChart graphique = dessin.createChart(ancre);
        graphique.setTitleText(progression.titre());
        graphique.setTitleOverlay(false);

        XDDFCategoryAxis axeBas = graphique.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis axeGauche = graphique.createValueAxis(AxisPosition.LEFT);
        axeGauche.setTitle(progression.unite());
        axeGauche.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> dates = XDDFDataSourcesFactory.fromStringCellRange(feuille,
                new CellRangeAddress(progression.ligneDates(), progression.ligneDates(), premiereColonne,
                        derniereColonne));
        XDDFNumericalDataSource<Double> valeurs = XDDFDataSourcesFactory.fromNumericCellRange(feuille,
                new CellRangeAddress(progression.ligneDonnees(), progression.ligneDonnees(), premiereColonne,
                        derniereColonne));
        XDDFLineChartData donnees = (XDDFLineChartData) graphique.createData(ChartTypes.LINE, axeBas, axeGauche);
        XDDFLineChartData.Series courbe = (XDDFLineChartData.Series) donnees.addSeries(dates, valeurs);
        courbe.setSmooth(false);
        courbe.setMarkerStyle(MarkerStyle.CIRCLE);
        graphique.plot(donnees);

        XDDFLineProperties trait = new XDDFLineProperties();
        trait.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(rgb(COULEUR_COURBE))));
        trait.setWidth(EPAISSEUR_COURBE_EMU);
        XDDFShapeProperties formes = new XDDFShapeProperties();
        formes.setLineProperties(trait);
        courbe.setShapeProperties(formes);
    }

    private XSSFCellStyle style(Cellule.Style modele) {
        return styles.computeIfAbsent(modele, this::creerStyle);
    }

    private XSSFCellStyle creerStyle(Cellule.Style modele) {
        XSSFFont police = classeur.createFont();
        police.setFontName(POLICE);
        police.setBold(modele.gras());
        police.setItalic(modele.italique());
        police.setFontHeightInPoints((short) modele.taille());
        police.setColor(couleur(modele.texte()));

        XSSFCellStyle style = classeur.createCellStyle();
        style.setFont(police);
        if (modele.fond() != null) {
            style.setFillForegroundColor(couleur(modele.fond()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setAlignment(
                "CENTER".equals(modele.alignement()) ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(modele.retourLigne());
        if (modele.formatNombre() != null) {
            style.setDataFormat(classeur.createDataFormat().getFormat(modele.formatNombre()));
        }
        return style;
    }

    private CellRangeAddress adresse(Plage plage) {
        return new CellRangeAddress(plage.ligneDebut(), plage.ligneFin() - 1, plage.colonneDebut(),
                plage.colonneFin() - 1);
    }

    private XSSFColor couleur(String hexadecimal) {
        return new XSSFColor(rgb(hexadecimal), null);
    }

    private byte[] rgb(String hexadecimal) {
        return new byte[]{(byte) Integer.parseInt(hexadecimal.substring(1, 3), 16),
                (byte) Integer.parseInt(hexadecimal.substring(3, 5), 16),
                (byte) Integer.parseInt(hexadecimal.substring(5, 7), 16)};
    }
}
