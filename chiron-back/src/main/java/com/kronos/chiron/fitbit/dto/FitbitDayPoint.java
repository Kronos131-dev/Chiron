package com.kronos.chiron.fitbit.dto;

import java.time.LocalDate;

public record FitbitDayPoint(LocalDate date, Integer steps, Double sleepHours) {
}
