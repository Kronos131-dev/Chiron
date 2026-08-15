package com.kronos.chiron.stats.service;

import com.kronos.chiron.stats.dto.BodyCompositionStatsDto;
import com.kronos.chiron.stats.dto.BodyweightStatsDto;
import com.kronos.chiron.stats.dto.ExerciseListItemDto;
import com.kronos.chiron.stats.dto.ExerciseProgressPointDto;
import com.kronos.chiron.stats.dto.MuscleStatsDto;
import com.kronos.chiron.stats.dto.NutritionStatsDto;
import com.kronos.chiron.stats.dto.StatsOverviewDto;
import com.kronos.chiron.stats.dto.WeeklyVolumePointDto;

import java.util.List;

public interface StatsService {

    StatsOverviewDto getOverview(String username);

    List<WeeklyVolumePointDto> getWeeklyVolume(String username, int weeks);

    MuscleStatsDto getMuscleStats(String username, int days);

    List<ExerciseListItemDto> getExerciseList(String username);

    List<ExerciseProgressPointDto> getExerciseProgress(String username, String nom);

    NutritionStatsDto getNutrition(String username, int days);

    BodyweightStatsDto getBodyweight(String username, int days);

    BodyCompositionStatsDto getBodyComposition(String username, int days);
}
