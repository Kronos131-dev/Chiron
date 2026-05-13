package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExercisePerformanceDto;
import com.kronos.chiron.dto.PerformanceRecordDto;
import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.entity.ExerciseType;
import com.kronos.chiron.entity.PerformanceRecord;
import com.kronos.chiron.entity.PerformanceTier;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.PerformanceRecordRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRecordRepository performanceRecordRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Returns the full performance summary for a user:
     * best record per exercise, individual tiers, and overall tier (avg of top 4).
     */
    @Transactional(readOnly = true)
    public PerformanceSummaryDto getSummary(String username) {
        Utilisateur user = findUser(username);
        Double poidsCorps = user.getPoidsCorps();

        List<ExercisePerformanceDto> exercises = Arrays.stream(ExerciseType.values())
                .map(type -> buildExerciseDto(user.getId(), type, poidsCorps))
                .collect(Collectors.toList());

        PerformanceTier overall = computeOverallTier(exercises);

        return PerformanceSummaryDto.builder()
                .overallTier(overall.getNom())
                .overallTierLevel(overall.getLevel())
                .overallTierCategorie(overall.getCategorie())
                .poidsCorps(poidsCorps)
                .exercises(exercises)
                .build();
    }

    /**
     * Records a new performance entry for a given exercise and returns the updated exercise summary.
     */
    @Transactional
    public ExercisePerformanceDto addRecord(String username, PerformanceRecordDto dto) {
        Utilisateur user = findUser(username);
        ExerciseType type = parseExerciseType(dto.getExerciseType());

        if (dto.getNombreReps() < 1 || dto.getNombreReps() > 36) {
            throw new IllegalArgumentException("Le nombre de répétitions doit être entre 1 et 36.");
        }

        Double poidsCorps = user.getPoidsCorps();
        double rm1 = calculateRm1(type, dto.getPoids(), dto.getNombreReps(), poidsCorps);
        Double ratio = (poidsCorps != null && poidsCorps > 0) ? computeRatio(type, rm1, poidsCorps) : null;

        PerformanceRecord record = PerformanceRecord.builder()
                .utilisateur(user)
                .exerciseType(type)
                .poids(dto.getPoids())
                .nombreReps(dto.getNombreReps())
                .rm1Estime(rm1)
                .ratioPerformance(ratio)
                .poidsCorporel(poidsCorps)
                .build();

        performanceRecordRepository.save(record);
        return buildExerciseDto(user.getId(), type, poidsCorps);
    }

    /**
     * Updates the user's bodyweight and returns the refreshed performance summary.
     */
    @Transactional
    public PerformanceSummaryDto updateBodyweight(String username, Double poidsCorps) {
        Utilisateur user = findUser(username);
        user.setPoidsCorps(poidsCorps);
        utilisateurRepository.save(user);
        return getSummary(username);
    }

    /**
     * Returns the full history of records for one exercise (most recent first).
     */
    @Transactional(readOnly = true)
    public List<ExercisePerformanceDto> getHistory(String username, String exerciseTypeName) {
        Utilisateur user = findUser(username);
        ExerciseType type = parseExerciseType(exerciseTypeName);

        return performanceRecordRepository
                .findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(user.getId(), type)
                .stream()
                .map(r -> toDto(r, tierForRatio(type, r.getRatioPerformance())))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------

    private ExercisePerformanceDto buildExerciseDto(Long userId, ExerciseType type, Double poidsCorps) {
        Optional<PerformanceRecord> best = performanceRecordRepository
                .findFirstByUtilisateurIdAndExerciseTypeOrderByRm1EstimeDesc(userId, type);

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
        PerformanceTier tier = tierForRatio(type, r.getRatioPerformance());
        return toDto(r, tier);
    }

    private ExercisePerformanceDto toDto(PerformanceRecord r, PerformanceTier tier) {
        return ExercisePerformanceDto.builder()
                .exerciseType(r.getExerciseType().name())
                .nom(r.getExerciseType().getNom())
                .poids(r.getPoids())
                .nombreReps(r.getNombreReps())
                .rm1Estime(round2(r.getRm1Estime()))
                .ratioPerformance(r.getRatioPerformance() != null ? round2(r.getRatioPerformance()) : null)
                .poidsCorporel(r.getPoidsCorporel())
                .tier(tier.getNom())
                .tierLevel(tier.getLevel())
                .tierCategorie(tier.getCategorie())
                .recordedAt(r.getRecordedAt())
                .build();
    }

    /**
     * 1RM formula: poids × (36 / (37 - reps)).
     * For bodyweight exercises, poids is extra weight; bodyweight is added before formula.
     */
    double calculateRm1(ExerciseType type, double poids, int reps, Double poidsCorps) {
        double effectiveWeight = (type.isBodyweightExercise() && poidsCorps != null)
                ? poids + poidsCorps
                : poids;
        return effectiveWeight * (36.0 / (37 - reps));
    }

    private double computeRatio(ExerciseType type, double rm1, double poidsCorps) {
        // For bodyweight exercises rm1 already includes bodyweight, so ratio = rm1 / poidsCorps
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
                PerformanceTier.DEMI_DIEU,
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

    /**
     * Overall tier = floor-average of the top-4 exercise tier levels.
     */
    private PerformanceTier computeOverallTier(List<ExercisePerformanceDto> exercises) {
        List<Integer> levels = exercises.stream()
                .map(ExercisePerformanceDto::getTierLevel)
                .sorted(Comparator.reverseOrder())
                .limit(4)
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
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + username));
    }

    private ExerciseType parseExerciseType(String name) {
        try {
            return ExerciseType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type d'exercice inconnu : " + name);
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
