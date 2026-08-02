package com.kronos.chiron.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ResponseEntity<StatsOverviewDto> overview(Authentication auth) {
        return ResponseEntity.ok(statsService.getOverview(auth.getName()));
    }

    @GetMapping("/volume")
    public ResponseEntity<List<WeeklyVolumePointDto>> volume(
            @RequestParam(defaultValue = "12") int weeks, Authentication auth) {
        return ResponseEntity.ok(statsService.getWeeklyVolume(auth.getName(), weeks));
    }

    @GetMapping("/muscles")
    public ResponseEntity<MuscleStatsDto> muscles(
            @RequestParam(defaultValue = "30") int days, Authentication auth) {
        return ResponseEntity.ok(statsService.getMuscleStats(auth.getName(), days));
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseListItemDto>> exercises(Authentication auth) {
        return ResponseEntity.ok(statsService.getExerciseList(auth.getName()));
    }

    @GetMapping("/exercises/progress")
    public ResponseEntity<List<ExerciseProgressPointDto>> exerciseProgress(
            @RequestParam String nom, Authentication auth) {
        return ResponseEntity.ok(statsService.getExerciseProgress(auth.getName(), nom));
    }

    @GetMapping("/nutrition")
    public ResponseEntity<NutritionStatsDto> nutrition(
            @RequestParam(defaultValue = "30") int days, Authentication auth) {
        return ResponseEntity.ok(statsService.getNutrition(auth.getName(), days));
    }

    @GetMapping("/bodyweight")
    public ResponseEntity<BodyweightStatsDto> bodyweight(
            @RequestParam(defaultValue = "90") int days, Authentication auth) {
        return ResponseEntity.ok(statsService.getBodyweight(auth.getName(), days));
    }

    @GetMapping("/body-composition")
    public ResponseEntity<BodyCompositionStatsDto> bodyComposition(
            @RequestParam(defaultValue = "180") int days, Authentication auth) {
        return ResponseEntity.ok(statsService.getBodyComposition(auth.getName(), days));
    }
}
