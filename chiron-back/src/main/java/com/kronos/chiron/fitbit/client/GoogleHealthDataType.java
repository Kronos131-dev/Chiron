package com.kronos.chiron.fitbit.client;

import java.time.LocalDate;

public enum GoogleHealthDataType {

    DAILY_RESTING_HEART_RATE("daily-resting-heart-rate", Operation.LIST,
            "daily_resting_heart_rate.date"), DAILY_HEART_RATE_VARIABILITY("daily-heart-rate-variability",
                    Operation.LIST, "daily_heart_rate_variability.date"), DAILY_RESPIRATORY_RATE("daily-respiratory-rate",
                                            Operation.LIST, "daily_respiratory_rate.date"), SLEEP("sleep",
                                                    Operation.LIST, "sleep.interval.civil_end_time"), STEPS("steps",
                                                            Operation.DAILY_ROLLUP, null), DISTANCE("distance",
                                                                    Operation.DAILY_ROLLUP, null), TOTAL_CALORIES(
                                                                            "total-calories", Operation.DAILY_ROLLUP,
                                                                            null), ACTIVE_ENERGY_BURNED(
                                                                                    "active-energy-burned",
                                                                                    Operation.DAILY_ROLLUP,
                                                                                    null), ACTIVE_ZONE_MINUTES(
                                                                                            "active-zone-minutes",
                                                                                            Operation.DAILY_ROLLUP,
                                                                                            null), TIME_IN_HEART_RATE_ZONE(
                                                                                                    "time-in-heart-rate-zone",
                                                                                                    Operation.DAILY_ROLLUP,
                                                                                                    null), HEART_RATE(
                                                                                                            "heart-rate",
                                                                                                            Operation.ROLLUP,
                                                                                                            null);

    public enum Operation {
        LIST, DAILY_ROLLUP, ROLLUP
    }

    private final String slug;
    private final Operation operation;
    private final String filterField;

    GoogleHealthDataType(String slug, Operation operation, String filterField) {
        this.slug = slug;
        this.operation = operation;
        this.filterField = filterField;
    }

    public Operation operation() {
        return operation;
    }

    public String dataPointsPath() {
        return "/v4/users/me/dataTypes/" + slug + "/dataPoints";
    }

    public String camelCaseField() {
        String[] parts = slug.split("-");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    public String filterFrom(LocalDate from) {
        if (filterField == null) {
            throw new IllegalStateException(name() + " ne supporte pas list, seulement rollup.");
        }
        return filterField + " >= \"" + from + "\"";
    }
}
