package com.kronos.chiron.exportation.service.classeur;

record Cellule(Object valeur, Style style) {

    static final Cellule VIDE = new Cellule(null, Style.NORMAL);

    static Cellule texte(String texte, Style style) {
        return new Cellule(texte, style);
    }

    static Cellule nombre(Double nombre, Style style) {
        return new Cellule(nombre, style);
    }

    static Cellule serie(double serial, Style style) {
        return new Cellule(Double.valueOf(serial), style);
    }

    record Style(String fond, String texte, boolean gras, boolean italique, int taille, String alignement,
            boolean retourLigne, String formatNombre) {

        static final Style NORMAL = new Style(null, "#0f172a", false, false, 10, "LEFT", false, null);

        Style avecFond(String nouveauFond) {
            return new Style(nouveauFond, texte, gras, italique, taille, alignement, retourLigne, formatNombre);
        }

        Style avecTexte(String nouveauTexte) {
            return new Style(fond, nouveauTexte, gras, italique, taille, alignement, retourLigne, formatNombre);
        }

        Style enGras() {
            return new Style(fond, texte, true, italique, taille, alignement, retourLigne, formatNombre);
        }

        Style enItalique() {
            return new Style(fond, texte, gras, true, taille, alignement, retourLigne, formatNombre);
        }

        Style avecTaille(int nouvelleTaille) {
            return new Style(fond, texte, gras, italique, nouvelleTaille, alignement, retourLigne, formatNombre);
        }

        Style centre() {
            return new Style(fond, texte, gras, italique, taille, "CENTER", retourLigne, formatNombre);
        }

        Style avecRetourLigne() {
            return new Style(fond, texte, gras, italique, taille, alignement, true, formatNombre);
        }

        Style avecFormatNombre(String format) {
            return new Style(fond, texte, gras, italique, taille, alignement, retourLigne, format);
        }
    }
}
