package com.kronos.chiron.fitbit;

import java.util.List;

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
        List<FitbitDayPoint> days) {
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
