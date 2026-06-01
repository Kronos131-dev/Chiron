package com.kronos.chiron.nutrition.olympusdb;

import com.kronos.chiron.stats.BodyweightPointDto;
import com.kronos.chiron.stats.NutritionPointDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Accès direct, en lecture seule, à la base Olympus.
 *
 * <p>Source unique de toutes les données nutrition / poids / profil de l'utilisateur.
 * La liaison se résout en un {@code olympus_user_id} (stocké côté Chiron) : toutes les
 * lectures se font ensuite par cet identifiant, sans aucune dépendance à l'API HTTP
 * d'Olympus.</p>
 *
 * <p>Pour les lectures « graphes » (séries journalières), toute erreur d'accès est avalée
 * et renvoie un résultat vide : la page Statistiques fonctionne en mode dégradé plutôt
 * que de planter. Les méthodes utilisées par les outils IA ({@link #findAuthByEmail},
 * {@link #profile}, {@link #dailyLog}, {@link #logEntries}, {@link #mealPresets},
 * {@link #weeklyPlan}) laissent au contraire remonter la {@link DataAccessException} pour
 * que l'appelant puisse signaler « service nutrition indisponible ».</p>
 */
@Repository
@Slf4j
public class OlympusNutritionDao {

    private final NamedParameterJdbcTemplate jdbc;

    public OlympusNutritionDao(@Qualifier("olympusJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------------------------------------------- Liaison

    /**
     * Récupère l'identifiant et le hash de mot de passe BCrypt d'un compte Olympus à
     * partir de son email (= pseudo de connexion). Utilisé une seule fois, à la liaison,
     * pour vérifier les identifiants saisis par l'utilisateur.
     */
    public Optional<AuthRow> findAuthByEmail(String email) {
        List<AuthRow> rows = jdbc.query(
                "SELECT id, password_hash FROM users WHERE email = :email LIMIT 1",
                new MapSqlParameterSource("email", email),
                (rs, i) -> new AuthRow(rs.getLong("id"), rs.getString("password_hash")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ------------------------------------------------------------------ Profil

    /** Profil Olympus (mensurations, objectif, cibles manuelles) pour le calcul des cibles. */
    public Optional<ProfileRow> profile(Long userId) {
        String sql = """
                SELECT current_weight_kg, height_cm, gender, birth_date,
                       activity_level, goal, auto_calculate_targets,
                       manual_target_kcal, manual_target_proteins,
                       manual_target_carbs, manual_target_fats
                FROM users WHERE id = :uid
                """;
        List<ProfileRow> rows = jdbc.query(sql, new MapSqlParameterSource("uid", userId),
                (rs, i) -> new ProfileRow(
                        nullableDouble(rs, "current_weight_kg"),
                        nullableDouble(rs, "height_cm"),
                        rs.getString("gender"),
                        rs.getObject("birth_date", LocalDate.class),
                        rs.getString("activity_level"),
                        rs.getString("goal"),
                        nullableBool(rs, "auto_calculate_targets"),
                        nullableDouble(rs, "manual_target_kcal"),
                        nullableDouble(rs, "manual_target_proteins"),
                        nullableDouble(rs, "manual_target_carbs"),
                        nullableDouble(rs, "manual_target_fats")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // -------------------------------------------------------------- Journal du jour

    /** Totaux nutritionnels + activité d'une journée donnée. */
    public Optional<DailyLogRow> dailyLog(Long userId, LocalDate date) {
        String sql = """
                SELECT total_kcal, total_proteins, total_carbs, total_fats,
                       step_count, workout_duration_minutes, extra_kcal_burned
                FROM daily_logs WHERE user_id = :uid AND target_date = :date
                """;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("uid", userId).addValue("date", date);
        List<DailyLogRow> rows = jdbc.query(sql, p, (rs, i) -> new DailyLogRow(
                nullableDouble(rs, "total_kcal"),
                nullableDouble(rs, "total_proteins"),
                nullableDouble(rs, "total_carbs"),
                nullableDouble(rs, "total_fats"),
                nullableInt(rs, "step_count"),
                nullableInt(rs, "workout_duration_minutes"),
                nullableDouble(rs, "extra_kcal_burned")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Détail repas par repas d'une journée : chaque entrée de journal avec son nom et ses
     * macros calculées (aliment = per-100g × quantité ; repas pré-enregistré = 1 portion
     * = somme de ses ingrédients).
     */
    public List<LogEntryRow> logEntries(Long userId, LocalDate date) {
        String sql = """
                SELECT le.quantity_grams,
                       fi.name AS food_name,
                       fi.kcal_100g, fi.proteins_100g, fi.carbs_100g, fi.fats_100g,
                       mp.name AS preset_name,
                       pt.kcal AS preset_kcal, pt.proteins AS preset_proteins,
                       pt.carbs AS preset_carbs, pt.fats AS preset_fats
                FROM log_entries le
                JOIN daily_logs dl ON dl.id = le.daily_log_id
                LEFT JOIN food_items fi ON fi.id = le.food_item_id
                LEFT JOIN meal_presets mp ON mp.id = le.meal_preset_id
                LEFT JOIN (
                    SELECT mi.meal_preset_id,
                           SUM(f.kcal_100g     * mi.quantity_grams / 100.0) AS kcal,
                           SUM(f.proteins_100g * mi.quantity_grams / 100.0) AS proteins,
                           SUM(f.carbs_100g    * mi.quantity_grams / 100.0) AS carbs,
                           SUM(f.fats_100g     * mi.quantity_grams / 100.0) AS fats
                    FROM meal_ingredients mi JOIN food_items f ON f.id = mi.food_item_id
                    GROUP BY mi.meal_preset_id
                ) pt ON pt.meal_preset_id = le.meal_preset_id
                WHERE dl.user_id = :uid AND dl.target_date = :date
                ORDER BY le.consumed_at
                """;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("uid", userId).addValue("date", date);
        return jdbc.query(sql, p, (rs, i) -> {
            String foodName = rs.getString("food_name");
            Double qty = nullableDouble(rs, "quantity_grams");
            if (foodName != null) {
                double ratio = (qty != null ? qty : 0.0) / 100.0;
                return new LogEntryRow(foodName, qty,
                        nullableDouble(rs, "kcal_100g") != null ? round1(nullableDouble(rs, "kcal_100g") * ratio) : 0.0,
                        nullableDouble(rs, "proteins_100g") != null ? round1(nullableDouble(rs, "proteins_100g") * ratio) : 0.0,
                        nullableDouble(rs, "carbs_100g") != null ? round1(nullableDouble(rs, "carbs_100g") * ratio) : 0.0,
                        nullableDouble(rs, "fats_100g") != null ? round1(nullableDouble(rs, "fats_100g") * ratio) : 0.0);
            }
            // Repas pré-enregistré : une entrée = 1 portion = total des ingrédients.
            String presetName = rs.getString("preset_name");
            return new LogEntryRow(presetName != null ? presetName : "Repas", null,
                    round1(orZero(nullableDouble(rs, "preset_kcal"))),
                    round1(orZero(nullableDouble(rs, "preset_proteins"))),
                    round1(orZero(nullableDouble(rs, "preset_carbs"))),
                    round1(orZero(nullableDouble(rs, "preset_fats"))));
        });
    }

    // ------------------------------------------------------------ Repas / planning

    /** Repas pré-enregistrés de l'utilisateur avec leurs valeurs nutritionnelles totales. */
    public List<MealPresetRow> mealPresets(Long userId) {
        String sql = """
                SELECT mp.name,
                       COALESCE(SUM(fi.kcal_100g     * mi.quantity_grams / 100.0), 0) AS kcal,
                       COALESCE(SUM(fi.proteins_100g * mi.quantity_grams / 100.0), 0) AS proteins,
                       COALESCE(SUM(fi.carbs_100g    * mi.quantity_grams / 100.0), 0) AS carbs,
                       COALESCE(SUM(fi.fats_100g     * mi.quantity_grams / 100.0), 0) AS fats
                FROM meal_presets mp
                LEFT JOIN meal_ingredients mi ON mi.meal_preset_id = mp.id
                LEFT JOIN food_items fi ON fi.id = mi.food_item_id
                WHERE mp.user_id = :uid
                GROUP BY mp.id, mp.name
                ORDER BY mp.name
                """;
        return jdbc.query(sql, new MapSqlParameterSource("uid", userId),
                (rs, i) -> new MealPresetRow(rs.getString("name"),
                        round1(rs.getDouble("kcal")), round1(rs.getDouble("proteins")),
                        round1(rs.getDouble("carbs")), round1(rs.getDouble("fats"))));
    }

    /** Repas planifiés hebdomadaires (plans actifs avec un jour de semaine défini). */
    public List<PlannedEntryRow> weeklyPlan(Long userId) {
        String sql = """
                SELECT pme.day_of_week, pme.quantity_grams,
                       fi.name AS food_name, mp.name AS preset_name
                FROM planned_meal_entries pme
                JOIN meal_plans plan ON plan.id = pme.meal_plan_id
                LEFT JOIN food_items fi ON fi.id = pme.food_item_id
                LEFT JOIN meal_presets mp ON mp.id = pme.meal_preset_id
                WHERE plan.user_id = :uid AND plan.active = true AND pme.day_of_week IS NOT NULL
                ORDER BY pme.day_of_week
                """;
        return jdbc.query(sql, new MapSqlParameterSource("uid", userId),
                (rs, i) -> new PlannedEntryRow(
                        rs.getString("day_of_week"),
                        nullableDouble(rs, "quantity_grams"),
                        rs.getString("food_name"),
                        rs.getString("preset_name")));
    }

    // --------------------------------------------------------- Séries (graphes stats)

    /**
     * Série journalière des apports nutritionnels (totaux déjà agrégés dans {@code daily_logs}).
     * L'objectif calorique du jour est le dernier objectif connu ({@code user_metrics.calorie_goal}).
     * Tolérant aux pannes (renvoie une liste vide).
     */
    public List<NutritionPointDto> dailyNutrition(Long userId, LocalDate start, LocalDate end) {
        String sql = """
                SELECT dl.target_date,
                       dl.total_kcal, dl.total_proteins, dl.total_carbs, dl.total_fats,
                       dl.step_count,
                       (SELECT um.calorie_goal FROM user_metrics um
                         WHERE um.user_id = dl.user_id AND um.recorded_date <= dl.target_date
                         ORDER BY um.recorded_date DESC LIMIT 1) AS target_kcal
                FROM daily_logs dl
                WHERE dl.user_id = :uid AND dl.target_date BETWEEN :start AND :end
                ORDER BY dl.target_date
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("uid", userId).addValue("start", start).addValue("end", end);
        try {
            return jdbc.query(sql, p, (rs, i) -> new NutritionPointDto(
                    rs.getObject("target_date", LocalDate.class),
                    nullableDouble(rs, "total_kcal"),
                    nullableDouble(rs, "total_proteins"),
                    nullableDouble(rs, "total_carbs"),
                    nullableDouble(rs, "total_fats"),
                    nullableDouble(rs, "target_kcal"),
                    nullableInt(rs, "step_count")));
        } catch (DataAccessException e) {
            log.warn("Olympus DB injoignable (dailyNutrition) : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Historique des mesures de poids de corps ({@code user_metrics}). Tolérant aux pannes. */
    public List<BodyweightPointDto> weightHistory(Long userId, LocalDate start, LocalDate end) {
        String sql = """
                SELECT recorded_date, weight_kg FROM user_metrics
                WHERE user_id = :uid AND recorded_date BETWEEN :start AND :end
                ORDER BY recorded_date
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("uid", userId).addValue("start", start).addValue("end", end);
        try {
            return jdbc.query(sql, p, (rs, i) -> new BodyweightPointDto(
                    rs.getObject("recorded_date", LocalDate.class),
                    rs.getDouble("weight_kg")));
        } catch (DataAccessException e) {
            log.warn("Olympus DB injoignable (weightHistory) : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ------------------------------------------------------------ Records (lignes)

    public record AuthRow(Long id, String passwordHash) {}

    public record ProfileRow(
            Double currentWeightKg, Double heightCm, String gender, LocalDate birthDate,
            String activityLevel, String goal, Boolean autoCalculateTargets,
            Double manualTargetKcal, Double manualTargetProteins,
            Double manualTargetCarbs, Double manualTargetFats) {}

    public record DailyLogRow(
            Double totalKcal, Double totalProteins, Double totalCarbs, Double totalFats,
            Integer stepCount, Integer workoutDurationMinutes, Double extraKcalBurned) {}

    /** Une entrée de journal, macros déjà calculées. quantityGrams null pour un repas pré-enregistré. */
    public record LogEntryRow(String name, Double quantityGrams,
                              double kcal, double proteins, double carbs, double fats) {}

    public record MealPresetRow(String name, double kcal, double proteins, double carbs, double fats) {}

    public record PlannedEntryRow(String dayOfWeek, Double quantityGrams, String foodName, String presetName) {}

    // ----------------------------------------------------------------- Helpers

    private Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private Boolean nullableBool(ResultSet rs, String col) throws SQLException {
        boolean v = rs.getBoolean(col);
        return rs.wasNull() ? null : v;
    }

    private static double orZero(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
