package com.kronos.chiron.stats;

/**
 * Indicateurs de tête pour la page Statistiques.
 *
 * @param seances30        nombre de séances réelles sur les 30 derniers jours
 * @param seancesTotal     nombre total de séances réelles
 * @param tonnageSemaine   volume total (kg soulevés) de la semaine ISO courante
 * @param streakSemaines   nombre de semaines consécutives (jusqu'à aujourd'hui) avec ≥1 séance
 * @param poidsCorps       poids de corps actuel (peut être null)
 * @param tier             palier global (nom)
 * @param tierLevel        niveau du palier global (1–8)
 * @param tierCategorie    catégorie du palier (Novice / Athlète / Légende)
 */
public record StatsOverviewDto(
        int seances30,
        int seancesTotal,
        double tonnageSemaine,
        int streakSemaines,
        Double poidsCorps,
        String tier,
        int tierLevel,
        String tierCategorie,
        Double dureeMoyenneMin
) {}
