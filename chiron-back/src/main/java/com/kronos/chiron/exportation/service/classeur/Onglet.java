package com.kronos.chiron.exportation.service.classeur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Onglet {

    static final int DERNIERES_COLONNES = -1;

    final String titre;
    final String couleurOnglet;
    final int lignesFigees;
    final int colonnesFigees;
    final List<List<Cellule>> lignes = new ArrayList<>();
    final Map<Integer, Integer> largeurs = new LinkedHashMap<>();
    final List<Plage> fusions = new ArrayList<>();
    final List<GraphiqueProgression> graphiques = new ArrayList<>();
    Plage filtre;
    int colonnesGrille;

    Onglet(String titre, String couleurOnglet, int lignesFigees, int colonnesFigees) {
        this.titre = titre;
        this.couleurOnglet = couleurOnglet;
        this.lignesFigees = lignesFigees;
        this.colonnesFigees = colonnesFigees;
    }

    int ajouterLigne(Cellule... cellules) {
        lignes.add(new ArrayList<>(List.of(cellules)));
        return lignes.size() - 1;
    }

    int ajouterLigne(List<Cellule> cellules) {
        lignes.add(new ArrayList<>(cellules));
        return lignes.size() - 1;
    }

    int ligneVide() {
        lignes.add(new ArrayList<>());
        return lignes.size() - 1;
    }
}
