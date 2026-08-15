package com.kronos.chiron.performance.service.impl;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;

import com.kronos.chiron.performance.service.PerformanceService;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.performance.dto.ExercisePerformanceDto;
import com.kronos.chiron.performance.dto.PerformanceRecordDto;
import com.kronos.chiron.performance.dto.PerformanceSummaryDto;
import com.kronos.chiron.seance.model.ExerciseType;
import com.kronos.chiron.performance.model.PerformanceRecord;
import com.kronos.chiron.performance.model.PerformanceTier;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.nutrition.service.NutritionService;
import com.kronos.chiron.nutrition.client.OlympusClient;
import com.kronos.chiron.performance.persistence.PerformanceRecordRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceServiceImpl implements PerformanceService {

    private final PerformanceRecordRepository performanceRecordRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NutritionService nutritionService;
    private final OlympusClient olympusClient;

    @Transactional(readOnly = true)
    @Override
    public PerformanceSummaryDto getSummary(String username) {
        Utilisateur user = findUser(username);
        Double poidsCorps = user.getPoidsCorps();

        List<ExercisePerformanceDto> exercises = buildAllExerciseDtos(user.getId(), poidsCorps);

        PerformanceTier overall = computeOverallTier(exercises);

        return PerformanceSummaryDto.builder()
                .overallTier(overall.getNom())
                .overallTierLevel(overall.getLevel())
                .overallTierCategorie(overall.getCategorie())
                .poidsCorps(poidsCorps)
                .exercises(exercises)
                .build();
    }

    @Transactional
    @Override
    public PerformanceSummaryDto addRecord(String username, PerformanceRecordDto dto) {
        Utilisateur user = findUser(username);
        ExerciseType type = parseExerciseType(dto.getExerciseType());

        if (dto.getNombreReps() < 1 || dto.getNombreReps() > 36) {
            throw badRequest("Le nombre de répétitions doit être entre 1 et 36.");
        }

        Double poidsCorps = user.getPoidsCorps();
        double rm1 = calculateRm1(type, dto.getPoids(), dto.getNombreReps(), poidsCorps);
        Double ratio = (poidsCorps != null && poidsCorps > 0) ? computeRatio(rm1, poidsCorps) : null;

        PerformanceRecord record = PerformanceRecord.builder()
                .utilisateur(user)
                .exerciseType(type)
                .poids(dto.getPoids())
                .nombreReps(dto.getNombreReps())
                .rm1Estime(rm1)
                .ratioPerformance(ratio)
                .poidsCorporel(poidsCorps)
                .build();

        performanceRecordRepository.saveAndFlush(record);

        List<ExercisePerformanceDto> exercises = buildAllExerciseDtos(user.getId(), poidsCorps);

        PerformanceTier overall = computeOverallTier(exercises);

        return PerformanceSummaryDto.builder()
                .overallTier(overall.getNom())
                .overallTierLevel(overall.getLevel())
                .overallTierCategorie(overall.getCategorie())
                .poidsCorps(poidsCorps)
                .exercises(exercises)
                .build();
    }

    @Transactional
    @Override
    public PerformanceSummaryDto updateBodyweight(String username, Double poidsCorps) {
        Utilisateur user = findUser(username);
        user.setPoidsCorps(poidsCorps);
        utilisateurRepository.save(user);
        syncWeightToOlympus(username, poidsCorps);
        return getSummary(username);
    }

    private void syncWeightToOlympus(String username, Double poidsCorps) {
        if (poidsCorps == null) {
            return;
        }
        try {
            String token = nutritionService.getValidToken(username);
            olympusClient.pushWeight(token, poidsCorps);
            log.info("OLYMPUS_WEIGHT_SYNCED user={} poids={}", username, poidsCorps);
        } catch (NutritionService.NotLinkedException | NutritionService.ExpiredException e) {
        } catch (OlympusClient.OlympusUnauthorizedException e) {
            log.warn("OLYMPUS_WEIGHT_SYNC_REJECTED user={} : token rejeté, liaison invalidée", username);
            nutritionService.invalidateLink(username);
        } catch (RuntimeException e) {
            log.warn("OLYMPUS_WEIGHT_SYNC_FAILED user={} : {}", username, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<ExercisePerformanceDto> getHistory(String username, String exerciseTypeName) {
        Utilisateur user = findUser(username);
        ExerciseType type = parseExerciseType(exerciseTypeName);
        Double poidsCorps = user.getPoidsCorps();

        return performanceRecordRepository
                .findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(user.getId(), type)
                .stream()
                .map(r -> {
                    Double ratio = (poidsCorps != null && poidsCorps > 0)
                            ? round2(computeRatio(r.getRm1Estime(), poidsCorps))
                            : r.getRatioPerformance();
                    return toDto(r, tierForRatio(type, ratio), ratio);
                })
                .collect(Collectors.toList());
    }

    private List<ExercisePerformanceDto> buildAllExerciseDtos(Long userId, Double poidsCorps) {
        Map<ExerciseType, PerformanceRecord> latestByType = performanceRecordRepository
                .findLatestPerExerciseTypeForUser(userId)
                .stream()
                .collect(Collectors.toMap(PerformanceRecord::getExerciseType, r -> r));

        return Arrays.stream(ExerciseType.values())
                .map(type -> buildExerciseDto(Optional.ofNullable(latestByType.get(type)), type, poidsCorps))
                .collect(Collectors.toList());
    }

    private ExercisePerformanceDto buildExerciseDto(Optional<PerformanceRecord> best, ExerciseType type,
            Double poidsCorps) {

        if (best.isEmpty()) {
            PerformanceTier ephebe = PerformanceTier.EPHEBE;
            return ExercisePerformanceDto.builder()
                    .exerciseType(type.name())
                    .nom(type.getNom())
                    .tier(ephebe.getNom())
                    .tierLevel(ephebe.getLevel())
                    .tierCategorie(ephebe.getCategorie())
                    .build();
        }

        PerformanceRecord r = best.get();

        Double currentRatio = (poidsCorps != null && poidsCorps > 0)
                ? round2(computeRatio(r.getRm1Estime(), poidsCorps))
                : null;

        PerformanceTier tier = tierForRatio(type, currentRatio);
        return toDto(r, tier, currentRatio);
    }

    private ExercisePerformanceDto toDto(PerformanceRecord r, PerformanceTier tier, Double ratio) {
        return ExercisePerformanceDto.builder()
                .exerciseType(r.getExerciseType().name())
                .nom(r.getExerciseType().getNom())
                .poids(r.getPoids())
                .nombreReps(r.getNombreReps())
                .rm1Estime(round2(r.getRm1Estime()))
                .ratioPerformance(ratio)
                .poidsCorporel(r.getPoidsCorporel())
                .tier(tier.getNom())
                .tierLevel(tier.getLevel())
                .tierCategorie(tier.getCategorie())
                .recordedAt(r.getRecordedAt())
                .build();
    }

    double calculateRm1(ExerciseType type, double poids, int reps, Double poidsCorps) {
        int effectiveReps = (type.isBodyweightExercise() && poids == 0.0) ? Math.min(reps, 10) : reps;
        double effectiveWeight = (type.isBodyweightExercise() && poidsCorps != null)
                ? poids + poidsCorps
                : poids;
        return effectiveWeight * (36.0 / (37 - effectiveReps));
    }

    private double computeRatio(double rm1, double poidsCorps) {
        return rm1 / poidsCorps;
    }

    PerformanceTier tierForRatio(ExerciseType type, Double ratio) {
        if (ratio == null) return PerformanceTier.EPHEBE;

        double[] thresholds = type.getThresholds();
        PerformanceTier[] tiers = {
                PerformanceTier.ARGONAUTE,
                PerformanceTier.HOPLITE,
                PerformanceTier.MYRMIDON,
                PerformanceTier.SPARTIATE,
                PerformanceTier.HEROS,
                PerformanceTier.DIEU,
                PerformanceTier.OLYMPIEN
        };

        PerformanceTier current = PerformanceTier.EPHEBE;
        for (int i = 0; i < thresholds.length; i++) {
            if (ratio >= thresholds[i]) {
                current = tiers[i];
            }
        }
        return current;
    }

    private PerformanceTier computeOverallTier(List<ExercisePerformanceDto> exercises) {
        List<Integer> levels = exercises.stream()
                .map(ExercisePerformanceDto::getTierLevel)
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .collect(Collectors.toList());

        if (levels.isEmpty()) return PerformanceTier.EPHEBE;

        double avg = levels.stream().mapToInt(Integer::intValue).average().orElse(1.0);
        int avgLevel = (int) Math.floor(avg);

        return Arrays.stream(PerformanceTier.values())
                .filter(t -> t.getLevel() == avgLevel)
                .findFirst()
                .orElse(PerformanceTier.EPHEBE);
    }

    private Utilisateur findUser(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + username));
    }

    private ExerciseType parseExerciseType(String name) {
        try {
            return ExerciseType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw badRequest("Type d'exercice inconnu : " + name);
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
