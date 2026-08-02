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

@Repository
@Slf4j
public class OlympusNutritionDao {

    private final NamedParameterJdbcTemplate jdbc;

    public OlympusNutritionDao(@Qualifier("olympusJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> resolveUserId(String linkToken) {
        try {
            List<Long> ids = jdbc.queryForList(
                    "SELECT user_id FROM integration_links WHERE token = :token AND revoked = false LIMIT 1",
                    new MapSqlParameterSource("token", linkToken),
                    Long.class);
            return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
        } catch (DataAccessException e) {
            log.warn("Olympus DB injoignable (resolveUserId) : {}", e.getMessage());
            return Optional.empty();
        }
    }

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

    private Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
