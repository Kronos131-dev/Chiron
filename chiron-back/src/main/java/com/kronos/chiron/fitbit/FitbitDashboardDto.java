package com.kronos.chiron.fitbit;

import java.util.List;

/**
 * Données du dashboard Fitbit renvoyées au front. Conçu pour un front simple :
 * {@code linked=false} → afficher un CTA de connexion ; {@code needsReconnect=true}
 * → afficher un CTA de reconnexion ; {@code dataAvailable=false} → Fitbit injoignable
 * mais compte lié.
 *
 * @param linked              le compte Fitbit est lié
 * @param needsReconnect      lié mais token irrécupérable — l'utilisateur doit relier
 * @param dataAvailable       les données ont pu être chargées depuis Fitbit
 * @param stepsToday          pas du jour
 * @param activeMinutesToday  minutes actives du jour (modérée + intense)
 * @param distanceTodayKm     distance parcourue aujourd'hui (km)
 * @param caloriesToday       calories dépensées aujourd'hui
 * @param sleepHoursLastNight heures de sommeil de la dernière nuit
 * @param restingHeartRate    fréquence cardiaque au repos (bpm)
 * @param days                série journalière (pas + sommeil) pour les graphes
 */
public record FitbitDashboardDto(
        boolean linked,
        boolean needsReconnect,
        boolean dataAvailable,
        Integer stepsToday,
        Integer activeMinutesToday,
        Double distanceTodayKm,
        Integer caloriesToday,
        Double sleepHoursLastNight,
        Integer restingHeartRate,
        List<FitbitDayPoint> days
) {
    public static FitbitDashboardDto notLinked() {
        return new FitbitDashboardDto(false, false, false, null, null, null, null, null, null, List.of());
    }

    public static FitbitDashboardDto reconnectNeeded() {
        return new FitbitDashboardDto(true, true, false, null, null, null, null, null, null, List.of());
    }

    public static FitbitDashboardDto unavailable() {
        return new FitbitDashboardDto(true, false, false, null, null, null, null, null, null, List.of());
    }
}
