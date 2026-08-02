package com.kronos.chiron.stats;

import com.kronos.chiron.performance.dto.PerformanceSummaryDto;
import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.exercice.model.MuscleGroup;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.exercice.model.TypeEquipement;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.performance.service.PerformanceService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.visbody.BodyCompositionRecord;
import com.kronos.chiron.visbody.BodyCompositionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Agrège les données d'entraînement (séances réelles) et, via Olympus, la nutrition
 * et le poids de corps, pour alimenter la page Statistiques. Toute l'agrégation est
 * faite côté serveur pour garder un payload léger et de bonnes perfs sur mobile.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final WeekFields ISO = WeekFields.ISO;

    private final SeanceRepository seanceRepository;
    private final PerformanceService performanceService;
    private final NutritionService nutritionService;
    private final OlympusNutritionDao olympusDao;
    private final BodyCompositionRecordRepository bodyCompositionRepo;
    private final UtilisateurRepository utilisateurRepository;

    // ---------------------------------------------------------------- Overview

    @Transactional(readOnly = true)
    public StatsOverviewDto getOverview(String username) {
        List<Seance> sessions = realSessions(username);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        long seances30 = sessions.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now.minusDays(30)))
                .count();

        TonnagePrefs tp = tonnagePrefs(username);
        String currentWeekKey = weekKey(today);
        double tonnageSemaine = sessions.stream()
                .filter(s -> s.getStartTime() != null)
                .filter(s -> weekKey(s.getStartTime().toLocalDate()).equals(currentWeekKey))
                .mapToDouble(s -> sessionTonnage(s, tp))
                .sum();

        int streak = computeWeekStreak(sessions, today);

        PerformanceSummaryDto perf = performanceService.getSummary(username);

        double dureeSum = 0;
        int dureeCount = 0;
        for (Seance s : sessions) {
            Double dm = sessionDurationMin(s);
            if (dm != null) { dureeSum += dm; dureeCount++; }
        }
        Double dureeMoyenneMin = dureeCount > 0 ? round1(dureeSum / dureeCount) : null;

        return new StatsOverviewDto(
                (int) seances30,
                sessions.size(),
                round1(tonnageSemaine),
                streak,
                perf.getPoidsCorps(),
                perf.getOverallTier(),
                perf.getOverallTierLevel(),
                perf.getOverallTierCategorie(),
                dureeMoyenneMin);
    }

    // ------------------------------------------------------------------ Volume

    @Transactional(readOnly = true)
    public List<WeeklyVolumePointDto> getWeeklyVolume(String username, int weeks) {
        int n = Math.min(Math.max(weeks, 1), 52);
        List<Seance> sessions = realSessions(username);
        TonnagePrefs tp = tonnagePrefs(username);
        LocalDate monday = LocalDate.now().with(ISO.dayOfWeek(), 1);

        List<WeeklyVolumePointDto> result = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate weekStart = monday.minusWeeks(i);
            LocalDate weekEnd = weekStart.plusDays(6);
            double tonnage = 0;
            int nbSeances = 0;
            int nbSeries = 0;
            double dureeSum = 0;
            int dureeCount = 0;
            for (Seance s : sessions) {
                if (s.getStartTime() == null) continue;
                LocalDate d = s.getStartTime().toLocalDate();
                if (d.isBefore(weekStart) || d.isAfter(weekEnd)) continue;
                nbSeances++;
                tonnage += sessionTonnage(s, tp);
                nbSeries += sessionSeriesCount(s);
                Double dm = sessionDurationMin(s);
                if (dm != null) { dureeSum += dm; dureeCount++; }
            }
            Double dureeMoyenneMin = dureeCount > 0 ? round1(dureeSum / dureeCount) : null;
            result.add(new WeeklyVolumePointDto(
                    weekStart, weekStart.format(LABEL), round1(tonnage), nbSeances, nbSeries, dureeMoyenneMin));
        }
        return result;
    }

    // ----------------------------------------------------------------- Muscles

    @Transactional(readOnly = true)
    public MuscleStatsDto getMuscleStats(String username, int days) {
        int n = Math.min(Math.max(days, 1), 365);
        LocalDateTime since = LocalDateTime.now().minusDays(n);
        List<Seance> sessions = realSessions(username);
        TonnagePrefs tp = tonnagePrefs(username);

        Map<MuscleGroup, double[]> tonnageAndSeries = new EnumMap<>(MuscleGroup.class); // [tonnage, nbSeries]
        Map<MuscleGroup, Set<Long>> seancesByMuscle = new EnumMap<>(MuscleGroup.class);

        for (Seance s : sessions) {
            if (s.getStartTime() == null || !s.getStartTime().isAfter(since)) continue;
            for (Exercice e : s.getExercices()) {
                MuscleGroup mg = (e.getDefinition() != null) ? e.getDefinition().getMusclePrincipal() : null;
                if (mg == null) continue;
                double[] acc = tonnageAndSeries.computeIfAbsent(mg, k -> new double[2]);
                acc[0] += exerciseTonnage(e, tp);
                acc[1] += e.getSeries().size();
                seancesByMuscle.computeIfAbsent(mg, k -> new java.util.HashSet<>()).add(s.getId());
            }
        }

        List<MuscleStatDto> muscles = new ArrayList<>();
        for (Map.Entry<MuscleGroup, double[]> entry : tonnageAndSeries.entrySet()) {
            muscles.add(new MuscleStatDto(
                    entry.getKey().name(),
                    round1(entry.getValue()[0]),
                    (int) entry.getValue()[1],
                    seancesByMuscle.getOrDefault(entry.getKey(), Set.of()).size()));
        }
        muscles.sort(Comparator.comparingDouble(MuscleStatDto::tonnage).reversed());

        List<String> negliges = new ArrayList<>();
        for (MuscleGroup mg : MuscleGroup.values()) {
            if (mg == MuscleGroup.CARDIO) continue;
            if (!tonnageAndSeries.containsKey(mg)) negliges.add(mg.name());
        }

        return new MuscleStatsDto(muscles, negliges);
    }

    // --------------------------------------------------------- Exercise lists

    @Transactional(readOnly = true)
    public List<ExerciseListItemDto> getExerciseList(String username) {
        List<Seance> sessions = realSessions(username);
        // clé = nom normalisé ; valeur = [nom affiché, set de séances, dernière date]
        Map<String, ExerciseAcc> byName = new LinkedHashMap<>();

        for (Seance s : sessions) {
            if (s.getStartTime() == null) continue;
            LocalDate date = s.getStartTime().toLocalDate();
            for (Exercice e : s.getExercices()) {
                if (e.getNom() == null || e.getNom().isBlank()) continue;
                String key = e.getNom().trim().toLowerCase();
                ExerciseAcc acc = byName.computeIfAbsent(key, k -> new ExerciseAcc(e.getNom().trim()));
                acc.seances.add(s.getId());
                if (acc.derniere == null || date.isAfter(acc.derniere)) acc.derniere = date;
            }
        }

        List<ExerciseListItemDto> result = new ArrayList<>();
        for (ExerciseAcc acc : byName.values()) {
            result.add(new ExerciseListItemDto(acc.nom, acc.seances.size(), acc.derniere));
        }
        result.sort(Comparator.comparingInt(ExerciseListItemDto::nbSeances).reversed()
                .thenComparing(ExerciseListItemDto::nom, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ExerciseProgressPointDto> getExerciseProgress(String username, String nom) {
        if (nom == null || nom.isBlank()) return List.of();
        String target = nom.trim().toLowerCase();
        List<Seance> sessions = realSessions(username);
        TonnagePrefs tp = tonnagePrefs(username);

        // sessions est trié décroissant : on parcourt en ordre chronologique croissant.
        List<Seance> chronological = new ArrayList<>(sessions);
        chronological.sort(Comparator.comparing(Seance::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<ExerciseProgressPointDto> points = new ArrayList<>();
        for (Seance s : chronological) {
            if (s.getStartTime() == null) continue;
            double chargeMax = 0, e1rm = 0, volume = 0;
            boolean found = false;
            for (Exercice e : s.getExercices()) {
                if (e.getNom() == null || !e.getNom().trim().toLowerCase().equals(target)) continue;
                found = true;
                for (Serie serie : e.getSeries()) {
                    chargeMax = Math.max(chargeMax, serie.getPoids());
                    e1rm = Math.max(e1rm, epley(serie.getPoids(), serie.getNombreReps()));
                }
                volume += exerciseTonnage(e, tp);
            }
            if (found) {
                points.add(new ExerciseProgressPointDto(
                        s.getStartTime().toLocalDate(), round1(chargeMax), round1(e1rm), round1(volume)));
            }
        }
        return points;
    }

    // --------------------------------------------------- Nutrition / poids (Olympus)

    @Transactional(readOnly = true)
    public NutritionStatsDto getNutrition(String username, int days) {
        int n = Math.min(Math.max(days, 1), 365);
        Optional<Long> userId = resolveOlympusUser(username);
        if (userId.isEmpty()) return NutritionStatsDto.notLinked();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(n - 1L);
        List<NutritionPointDto> jours = olympusDao.dailyNutrition(userId.get(), start, end);

        double sumK = 0, sumP = 0, sumG = 0, sumL = 0, sumT = 0;
        int nK = 0, nT = 0;
        for (NutritionPointDto j : jours) {
            if (j.kcal() != null) { sumK += j.kcal(); sumP += orZero(j.proteines()); sumG += orZero(j.glucides()); sumL += orZero(j.lipides()); nK++; }
            if (j.targetKcal() != null) { sumT += j.targetKcal(); nT++; }
        }

        return new NutritionStatsDto(
                true, jours,
                nK > 0 ? round1(sumK / nK) : null,
                nK > 0 ? round1(sumP / nK) : null,
                nK > 0 ? round1(sumG / nK) : null,
                nK > 0 ? round1(sumL / nK) : null,
                nT > 0 ? round1(sumT / nT) : null);
    }

    @Transactional(readOnly = true)
    public BodyweightStatsDto getBodyweight(String username, int days) {
        int n = Math.min(Math.max(days, 1), 730);
        Optional<Long> userId = resolveOlympusUser(username);
        if (userId.isEmpty()) return BodyweightStatsDto.notLinked();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(n - 1L);
        return new BodyweightStatsDto(true, olympusDao.weightHistory(userId.get(), start, end));
    }

    // ------------------------------------------------- Composition corporelle (Visbody)

    @Transactional(readOnly = true)
    public BodyCompositionStatsDto getBodyComposition(String username, int days) {
        int n = Math.min(Math.max(days, 1), 1825);
        Optional<Utilisateur> user = utilisateurRepository.findByUsername(username);
        if (user.isEmpty()) return BodyCompositionStatsDto.empty();

        LocalDateTime since = LocalDateTime.now().minusDays(n);
        List<BodyCompositionRecord> records =
                bodyCompositionRepo.findByUtilisateurAndMesureLeAfterOrderByMesureLeAsc(user.get(), since);
        if (records.isEmpty()) return BodyCompositionStatsDto.empty();

        List<BodyCompositionPointDto> points = new ArrayList<>();
        for (BodyCompositionRecord r : records) {
            points.add(new BodyCompositionPointDto(
                    r.getMesureLe().toLocalDate(),
                    r.getNote(),
                    r.getPoids(), r.getMasseMusculaire(), r.getMms(), r.getMgc(), r.getMmc(),
                    r.getTgcPct(), r.getImc(), r.getRth(), r.getMbKcal(), r.getAgeMetabolique(),
                    r.getGraisseViscerale(), r.getEauTotale(), r.getEauIntra(), r.getEauExtra(),
                    r.getRatioEcwTbw(), r.getMasseProteine(), r.getSelInorganique(),
                    r.getMgcBrasGauche(), r.getMgcBrasDroit(), r.getMgcTronc(),
                    r.getMgcJambeGauche(), r.getMgcJambeDroite(),
                    r.getMuscleBrasGauche(), r.getMuscleBrasDroit(), r.getMuscleTronc(),
                    r.getMuscleJambeGauche(), r.getMuscleJambeDroite()));
        }
        return new BodyCompositionStatsDto(true, points);
    }

    private Optional<Long> resolveOlympusUser(String username) {
        String token;
        try {
            token = nutritionService.getValidToken(username);
        } catch (NutritionService.NotLinkedException | NutritionService.ExpiredException e) {
            return Optional.empty();
        }
        return olympusDao.resolveUserId(token);
    }

    // ----------------------------------------------------------------- Helpers

    private List<Seance> realSessions(String username) {
        return seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(username);
    }

    /** Préférences de tonnage d'un utilisateur (conventions de saisie du poids). */
    private record TonnagePrefs(boolean halteresX2, boolean machineX2) {}

    /** Charge les préférences de tonnage de l'utilisateur (défauts : haltères ×2, machine ×1). */
    private TonnagePrefs tonnagePrefs(String username) {
        return utilisateurRepository.findByUsername(username)
                .map(u -> new TonnagePrefs(u.isPoidsHaltereParImplement(), u.isPoidsMachineParCote()))
                .orElse(new TonnagePrefs(true, false));
    }

    /**
     * Facteur ×1/×2 du tonnage d'un exercice, sans cumul (max ×2) :
     * unilatéral, ou haltères/machine selon les préférences de saisie de l'utilisateur.
     */
    private int tonnageFactor(Exercice e, TonnagePrefs p) {
        TypeEquipement eq = (e.getDefinition() != null) ? e.getDefinition().getTypeEquipement() : null;
        boolean doubler = e.isUnilateral()
                || (eq == TypeEquipement.HALTERES && p.halteresX2())
                || (eq == TypeEquipement.MACHINE && p.machineX2());
        return doubler ? 2 : 1;
    }

    private double sessionTonnage(Seance s, TonnagePrefs p) {
        double t = 0;
        for (Exercice e : s.getExercices()) t += exerciseTonnage(e, p);
        return t;
    }

    private double exerciseTonnage(Exercice e, TonnagePrefs p) {
        double t = 0;
        for (Serie serie : e.getSeries()) {
            t += serie.getPoids() * serie.getNombreReps();
            for (Degressif d : serie.getDegressifs()) {
                t += d.getPoids() * d.getNombreReps();
            }
        }
        return t * tonnageFactor(e, p);
    }

    private int sessionSeriesCount(Seance s) {
        int c = 0;
        for (Exercice e : s.getExercices()) c += e.getSeries().size();
        return c;
    }

    /** Durée d'une séance en minutes, ou null si début/fin manquants ou incohérents. */
    private Double sessionDurationMin(Seance s) {
        if (s.getStartTime() == null || s.getEndTime() == null) return null;
        long min = java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
        return min > 0 ? (double) min : null;
    }

    /** 1RM estimé (Epley), reps bornées à [1,36] pour éviter une division par zéro. */
    private double epley(double poids, int reps) {
        int r = Math.min(Math.max(reps, 1), 36);
        return poids * (36.0 / (37 - r));
    }

    private int computeWeekStreak(List<Seance> sessions, LocalDate today) {
        Set<String> weeks = new TreeSet<>();
        for (Seance s : sessions) {
            if (s.getStartTime() != null) weeks.add(weekKey(s.getStartTime().toLocalDate()));
        }
        int streak = 0;
        LocalDate cursor = today;
        while (weeks.contains(weekKey(cursor))) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    private String weekKey(LocalDate d) {
        return d.get(ISO.weekBasedYear()) + "-" + d.get(ISO.weekOfWeekBasedYear());
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double orZero(Double v) {
        return v == null ? 0.0 : v;
    }

    /** Accumulateur interne pour la liste des exercices. */
    private static final class ExerciseAcc {
        final String nom;
        final Set<Long> seances = new java.util.HashSet<>();
        LocalDate derniere;
        ExerciseAcc(String nom) { this.nom = nom; }
    }
}
