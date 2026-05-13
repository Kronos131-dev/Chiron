package com.kronos.chiron.entity;

import lombok.Getter;

@Getter
public enum PerformanceTier {

    EPHEBE(1, "Éphèbe", "Novice"),
    ARGONAUTE(2, "Argonaute", "Novice"),
    HOPLITE(3, "Hoplite", "Athlète"),
    MYRMIDON(4, "Myrmidon", "Athlète"),
    SPARTIATE(5, "Spartiate", "Athlète"),
    HEROS(6, "Héros", "Légende"),
    DEMI_DIEU(7, "Demi-Dieu", "Légende"),
    OLYMPIEN(8, "Olympien", "Légende");

    private final int level;
    private final String nom;
    private final String categorie;

    PerformanceTier(int level, String nom, String categorie) {
        this.level = level;
        this.nom = nom;
        this.categorie = categorie;
    }
}
