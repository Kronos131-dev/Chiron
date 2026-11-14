package com.kronos.chiron.exportation.service.classeur;

import com.kronos.chiron.exportation.service.classeur.Cellule.Style;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class ClasseurBuilder {

    private final ProgressionExercices progressions = new ProgressionExercices(Locale.FRANCE, " | ");

    private static final String ARDOISE = "#0f172a";
    private static final String ARDOISE_CLAIRE = "#1e293b";
    private static final String BRONZE = "#ffb779";
    private static final String BRONZE_FONCE = "#c77a30";
    private static final String BLANC = "#f8fafc";
    private static final String GRIS = "#64748b";
    private static final String GRIS_CLAIR = "#cbd5e1";
    private static final String FOND_LIGNE = "#f1f5f9";

    private static final Style BANDEAU = Style.NORMAL.avecFond(ARDOISE).avecTexte(BRONZE).enGras().avecTaille(16);
    private static final Style SOUS_BANDEAU = Style.NORMAL.avecFond(ARDOISE).avecTexte(GRIS_CLAIR).avecTaille(10);
    private static final Style MENTION = Style.NORMAL.avecFond(ARDOISE).avecTexte("#94a3b8").enItalique().avecTaille(9);
    private static final Style ENTETE = Style.NORMAL.avecFond(ARDOISE_CLAIRE).avecTexte(BLANC).enGras().centre();
    private static final Style ENTETE_GAUCHE = Style.NORMAL.avecFond(ARDOISE_CLAIRE).avecTexte(BLANC).enGras();
    private static final Style ENTETE_SECONDAIRE = Style.NORMAL.avecFond(ARDOISE_CLAIRE).avecTexte(GRIS_CLAIR)
            .avecTaille(9).centre();
    private static final Style TITRE_BLOC = Style.NORMAL.avecFond(BRONZE).avecTexte(ARDOISE).enGras();
    private static final Style NOM = Style.NORMAL.enGras();
    private static final Style UNITE = Style.NORMAL.avecTexte(GRIS).centre();
    private static final Style VALEUR = Style.NORMAL.centre().enGras();
    private static final Style TEXTE_CENTRE = Style.NORMAL.centre().avecRetourLigne();
    private static final Style TEXTE_LIGNE = Style.NORMAL.avecRetourLigne();
    private static final Style MESSAGE = Style.NORMAL.enItalique().avecTexte(GRIS);

    private static final int LARGEUR_NOM = 230;
    private static final int LARGEUR_UNITE = 70;
    private static final int LARGEUR_SEANCE = 130;
    private static final int COLONNES_FIGEES = 2;
    private static final int LIGNES_FIGEES_MODELE = 5;
    private static final int GRAPHIQUE_LIGNES = 16;
    private static final int GRAPHIQUE_COLONNE_DROITE = COLONNES_FIGEES + 5;
    private static final int COLONNES_MINIMUM = 14;

    private static final DateTimeFormatter COURT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter COURT_AVEC_ANNEE = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final LocalDate ORIGINE_EXCEL = LocalDate.of(1899, 12, 30);

    private static final int LONGUEUR_MAX_ONGLET = 31;
    private static final String RESERVE_SYNTHESE = "synthèse";
    private static final String RESERVE_HISTORIQUE = "historique";

    List<Onglet> onglets(DonneesExport donnees) {
        Set<String> titresPris = new HashSet<>(Set.of(RESERVE_SYNTHESE, RESERVE_HISTORIQUE));
        List<Onglet> onglets = new ArrayList<>();
        onglets.add(ongletSynthese(donnees));
        for (ModeleDonnees modele : donnees.modeles()) {
            onglets.add(ongletModele(modele, nomUnique(modele.titre(), titresPris), donnees.genereLe()));
        }
        onglets.add(ongletHistorique(donnees));
        return onglets;
    }

    private Onglet ongletModele(ModeleDonnees modele, String titre, LocalDateTime genereLe) {
        Onglet onglet = new Onglet(titre, ARDOISE_CLAIRE, LIGNES_FIGEES_MODELE, COLONNES_FIGEES);
        List<SeanceDonnees> seances = new ArrayList<>(modele.seances());
        seances.sort(Comparator.comparing(SeanceDonnees::date));
        int n = seances.size();
        int dernierCol = Math.max(COLONNES_FIGEES + n, 6);
        List<LigneProgression> lignes = progressions.calculer(modele, seances);

        remplirBandeau(onglet, modele.titre(), sousTitre(seances, genereLe), dernierCol);

        List<Cellule> dates = new ArrayList<>(List.of(Cellule.texte("CHARGE MAX", ENTETE_GAUCHE),
                Cellule.texte("Unité", ENTETE)));
        List<Cellule> titres = new ArrayList<>(List.of(Cellule.texte("", ENTETE_SECONDAIRE),
                Cellule.texte("", ENTETE_SECONDAIRE)));
        boolean plusieursAnnees = seances.stream().map(s -> s.date().getYear()).distinct().count() > 1;
        for (SeanceDonnees seance : seances) {
            dates.add(Cellule.texte(seance.date().format(plusieursAnnees ? COURT_AVEC_ANNEE : COURT), ENTETE));
            titres.add(
                    Cellule.texte(seance.titre() == null ? "" : seance.titre(), ENTETE_SECONDAIRE.avecRetourLigne()));
        }
        int ligneDates = onglet.ajouterLigne(dates);
        onglet.ajouterLigne(titres);

        onglet.largeurs.put(0, LARGEUR_NOM);
        onglet.largeurs.put(1, LARGEUR_UNITE);
        onglet.largeurs.put(Onglet.DERNIERES_COLONNES, LARGEUR_SEANCE);
        onglet.colonnesGrille = Math.max(COLONNES_MINIMUM, COLONNES_FIGEES + n + 1);

        if (n == 0 || lignes.isEmpty()) {
            int ligne = onglet.ajouterLigne(Cellule.texte(
                    "Aucune séance enregistrée pour l'instant : elle apparaîtra ici dès la prochaine.", MESSAGE));
            onglet.fusions.add(new Plage(ligne, ligne + 1, 0, dernierCol));
            return onglet;
        }

        int debutCharge = onglet.lignes.size();
        for (LigneProgression ligne : lignes) {
            List<Cellule> cellules = new ArrayList<>(List.of(Cellule.texte(ligne.nom(), NOM),
                    Cellule.texte(ligne.unite(), UNITE)));
            for (Double valeur : ligne.valeurs()) {
                cellules.add(valeur == null ? new Cellule(null, VALEUR) : Cellule.nombre(valeur, VALEUR));
            }
            onglet.ajouterLigne(cellules);
        }
        onglet.ligneVide();

        blocTexte(onglet, "SÉRIES", lignes, n, LigneProgression::series);
        onglet.ligneVide();
        int debutCommentaires = blocTexte(onglet, "COMMENTAIRES", lignes, n, LigneProgression::commentaires);
        List<Cellule> notes = new ArrayList<>(List.of(Cellule.texte("Note de séance", NOM.enItalique()),
                Cellule.VIDE));
        for (SeanceDonnees seance : seances) {
            notes.add(Cellule.texte(seance.note() == null ? "" : seance.note(), TEXTE_LIGNE));
        }
        int ligneNotes = onglet.ajouterLigne(notes);
        onglet.ligneVide();

        int ligneTitreGraphiques = onglet.ajouterLigne(titreDeBloc("PROGRESSION", n));
        onglet.fusions.add(new Plage(ligneTitreGraphiques, ligneTitreGraphiques + 1, 0, dernierCol));
        int hautGraphiques = onglet.lignes.size() + 1;
        int rang = 0;
        for (int i = 0; i < lignes.size(); i++) {
            LigneProgression ligne = lignes.get(i);
            if (ligne.valeurs().stream().allMatch(Objects::isNull)) continue;
            int bande = rang / 2;
            int colonne = rang % 2 == 0 ? COLONNES_FIGEES : GRAPHIQUE_COLONNE_DROITE;
            onglet.graphiques.add(new GraphiqueProgression(ligne.nom() + " (" + ligne.unite() + ")", ligne.unite(),
                    debutCharge + i, ligneDates, n, hautGraphiques + bande * GRAPHIQUE_LIGNES, colonne));
            rang++;
        }
        int bandes = (rang + 1) / 2;
        return onglet;
    }

    private int blocTexte(Onglet onglet, String titre, List<LigneProgression> lignes, int n,
            java.util.function.Function<LigneProgression, List<String>> source) {
        int ligneTitre = onglet.ajouterLigne(titreDeBloc(titre, n));
        onglet.fusions.add(new Plage(ligneTitre, ligneTitre + 1, 0, COLONNES_FIGEES + Math.max(n, 4)));
        for (LigneProgression ligne : lignes) {
            List<Cellule> cellules = new ArrayList<>(List.of(Cellule.texte(ligne.nom(), NOM), Cellule.VIDE));
            for (String texte : source.apply(ligne)) {
                cellules.add(Cellule.texte(texte, TEXTE_CENTRE));
            }
            onglet.ajouterLigne(cellules);
        }
        return ligneTitre + 1;
    }

    private List<Cellule> titreDeBloc(String titre, int n) {
        List<Cellule> cellules = new ArrayList<>(List.of(Cellule.texte(titre, TITRE_BLOC)));
        for (int i = 0; i < COLONNES_FIGEES - 1 + Math.max(n, 4); i++) {
            cellules.add(new Cellule(null, TITRE_BLOC));
        }
        return cellules;
    }

    private void remplirBandeau(Onglet onglet, String titre, String sousTitre, int dernierCol) {
        for (int i = 0; i < 3; i++) {
            List<Cellule> ligne = new ArrayList<>();
            Cellule premiere = switch (i) {
                case 0 -> Cellule.texte(titre, BANDEAU);
                case 1 -> Cellule.texte(sousTitre, SOUS_BANDEAU);
                default -> Cellule.texte(
                        "Fichier généré par Chiron : télécharge-le à nouveau pour y retrouver tes dernières séances.",
                        MENTION);
            };
            ligne.add(premiere);
            Style style = i == 0 ? BANDEAU : i == 1 ? SOUS_BANDEAU : MENTION;
            for (int c = 1; c < dernierCol; c++) {
                ligne.add(new Cellule(null, style));
            }
            int index = onglet.ajouterLigne(ligne);
            onglet.fusions.add(new Plage(index, index + 1, 0, dernierCol));
        }
    }

    private String sousTitre(List<SeanceDonnees> seances, LocalDateTime genereLe) {
        String maj = "généré le " + genereLe.toLocalDate().format(LONG) + " à " + genereLe.format(HEURE);
        if (seances.isEmpty()) return "Aucune séance · " + maj;
        String debut = seances.get(0).date().toLocalDate().format(LONG);
        String fin = seances.get(seances.size() - 1).date().toLocalDate().format(LONG);
        return seances.size() + (seances.size() > 1 ? " séances" : " séance") + " · du " + debut + " au " + fin
                + " · " + maj;
    }

    private Onglet ongletSynthese(DonneesExport donnees) {
        Onglet onglet = new Onglet("Synthèse", BRONZE, 4, 0);
        remplirBandeau(onglet, "SYNTHÈSE · " + donnees.nom(),
                "Première charge, dernière charge et record de chaque exercice · généré le "
                        + donnees.genereLe().toLocalDate().format(LONG) + " à " + donnees.genereLe().format(HEURE),
                8);
        onglet.ajouterLigne(List.of(Cellule.texte("Séance", ENTETE_GAUCHE), Cellule.texte("Exercice", ENTETE_GAUCHE),
                Cellule.texte("Unité", ENTETE), Cellule.texte("Première", ENTETE), Cellule.texte("Dernière", ENTETE),
                Cellule.texte("Record", ENTETE), Cellule.texte("Date du record", ENTETE),
                Cellule.texte("Séances", ENTETE)));
        for (ModeleDonnees modele : donnees.modeles()) {
            List<SeanceDonnees> seances = new ArrayList<>(modele.seances());
            seances.sort(Comparator.comparing(SeanceDonnees::date));
            List<LigneProgression> lignes = progressions.calculer(modele, seances);
            if (lignes.isEmpty()) {
                onglet.ajouterLigne(List.of(Cellule.texte(modele.titre(), NOM),
                        Cellule.texte("Aucune séance enregistrée", MESSAGE)));
                continue;
            }
            for (LigneProgression ligne : lignes) {
                onglet.ajouterLigne(ligneSynthese(modele.titre(), ligne, seances));
            }
        }
        onglet.largeurs.put(0, 200);
        onglet.largeurs.put(1, 240);
        onglet.largeurs.put(2, 80);
        onglet.largeurs.put(3, 100);
        onglet.largeurs.put(4, 100);
        onglet.largeurs.put(5, 100);
        onglet.largeurs.put(6, 130);
        onglet.largeurs.put(7, 90);
        onglet.colonnesGrille = 8;
        return onglet;
    }

    private List<Cellule> ligneSynthese(String titreModele, LigneProgression ligne, List<SeanceDonnees> seances) {
        Double premiere = null;
        Double derniere = null;
        Double record = null;
        LocalDateTime dateRecord = null;
        int compte = 0;
        for (int i = 0; i < ligne.valeurs().size(); i++) {
            Double valeur = ligne.valeurs().get(i);
            if (valeur == null) continue;
            compte++;
            if (premiere == null) premiere = valeur;
            derniere = valeur;
            if (record == null || valeur > record) {
                record = valeur;
                dateRecord = seances.get(i).date();
            }
        }
        return List.of(Cellule.texte(titreModele, NOM), Cellule.texte(ligne.nom(), TEXTE_LIGNE),
                Cellule.texte(ligne.unite(), UNITE), cellule(premiere), cellule(derniere), cellule(record),
                Cellule.texte(dateRecord == null ? "" : dateRecord.toLocalDate().format(LONG), TEXTE_CENTRE),
                Cellule.nombre((double) compte, VALEUR));
    }

    private Cellule cellule(Double valeur) {
        return valeur == null ? new Cellule(null, VALEUR) : Cellule.nombre(valeur, VALEUR);
    }

    private Onglet ongletHistorique(DonneesExport donnees) {
        Onglet onglet = new Onglet("Historique", GRIS, 1, 0);
        onglet.ajouterLigne(List.of(Cellule.texte("Date", ENTETE), Cellule.texte("Séance", ENTETE_GAUCHE),
                Cellule.texte("Programme", ENTETE_GAUCHE), Cellule.texte("Exercice", ENTETE_GAUCHE),
                Cellule.texte("Séries", ENTETE_GAUCHE), Cellule.texte("Charge max", ENTETE),
                Cellule.texte("Unité", ENTETE), Cellule.texte("Commentaire", ENTETE_GAUCHE),
                Cellule.texte("Note de séance", ENTETE_GAUCHE)));
        record Entree(LocalDateTime date, String seance, String programme, String exercice, String series,
                Double valeur, String unite, String commentaire, String note) {
        }
        List<Entree> entrees = new ArrayList<>();
        for (ModeleDonnees modele : donnees.modeles()) {
            List<SeanceDonnees> seances = new ArrayList<>(modele.seances());
            seances.sort(Comparator.comparing(SeanceDonnees::date));
            List<LigneProgression> lignes = progressions.calculer(modele, seances);
            for (int i = 0; i < seances.size(); i++) {
                SeanceDonnees seance = seances.get(i);
                boolean premiere = true;
                for (LigneProgression ligne : lignes) {
                    if (ligne.valeurs().get(i) == null) continue;
                    entrees.add(new Entree(seance.date(), seance.titre(), modele.titre(), ligne.nom(),
                            ligne.series().get(i), ligne.valeurs().get(i), ligne.unite(), ligne.commentaires().get(i),
                            premiere && seance.note() != null ? seance.note() : ""));
                    premiere = false;
                }
            }
        }
        entrees.sort(Comparator.comparing(Entree::date).reversed());
        Style date = Style.NORMAL.centre().avecFormatNombre("dd/mm/yyyy");
        for (Entree entree : entrees) {
            onglet.ajouterLigne(List.of(
                    Cellule.serie(serial(entree.date()), date),
                    Cellule.texte(entree.seance() == null ? "" : entree.seance(), TEXTE_LIGNE),
                    Cellule.texte(entree.programme(), TEXTE_LIGNE), Cellule.texte(entree.exercice(), NOM),
                    Cellule.texte(entree.series(), TEXTE_LIGNE), Cellule.nombre(entree.valeur(), VALEUR),
                    Cellule.texte(entree.unite(), UNITE), Cellule.texte(entree.commentaire(), TEXTE_LIGNE),
                    Cellule.texte(entree.note(), TEXTE_LIGNE)));
        }
        onglet.largeurs.put(0, 100);
        onglet.largeurs.put(1, 170);
        onglet.largeurs.put(2, 170);
        onglet.largeurs.put(3, 220);
        onglet.largeurs.put(4, 280);
        onglet.largeurs.put(5, 100);
        onglet.largeurs.put(6, 70);
        onglet.largeurs.put(7, 260);
        onglet.largeurs.put(8, 260);
        onglet.colonnesGrille = 9;
        onglet.filtre = new Plage(0, Math.max(onglet.lignes.size(), 2), 0, 9);
        return onglet;
    }

    private double serial(LocalDateTime date) {
        return ChronoUnit.DAYS.between(ORIGINE_EXCEL, date.toLocalDate());
    }

    static String nomUnique(String titre, Set<String> pris) {
        String base = titre == null ? "" : titre.replaceAll("[\\[\\]*?:/\\\\]", " ").replaceAll("\\s+", " ").trim();
        base = base.replaceAll("^'+|'+$", "").trim();
        if (base.isEmpty()) base = "Séance";
        String nom = tronquer(base, LONGUEUR_MAX_ONGLET);
        int suffixe = 2;
        while (pris.contains(nom.toLowerCase(Locale.ROOT))) {
            String fin = " (" + suffixe++ + ")";
            nom = tronquer(base, LONGUEUR_MAX_ONGLET - fin.length()) + fin;
        }
        pris.add(nom.toLowerCase(Locale.ROOT));
        return nom;
    }

    private static String tronquer(String texte, int longueur) {
        return texte.length() <= longueur ? texte : texte.substring(0, longueur).trim();
    }
}
