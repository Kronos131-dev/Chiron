package com.kronos.chiron.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies on a real PostgreSQL that deleting an account takes its outdoor runs with it.
 *
 * Until V57, course_trace was the only user-owned table whose foreign key did not cascade:
 * deleting an athlete who had ever run outdoors raised an integrity violation that the
 * profile endpoint returned as an opaque 400.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("schema-it")
@Import(SuppressionCompteCascadeTest.MailStubConfig.class)
class SuppressionCompteCascadeTest {

    @TestConfiguration
    static class MailStubConfig {
        @Bean
        JavaMailSender javaMailSender() {
            return new JavaMailSenderImpl();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrides(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void suppressionUtilisateur_avecTraceDeCourse_supprimeLaTraceEnCascade() {
        // Given
        Long utilisateurId = creerUtilisateur("coureur-a-supprimer");
        Long traceId = creerTrace(utilisateurId);

        // When
        jdbc.update("DELETE FROM utilisateur WHERE id = ?", utilisateurId);

        // Then
        assertThat(compterTraces(traceId)).isZero();
    }

    @Test
    void suppressionTrace_serieQuiLaReference_remetLePointeurANull() {
        // Given
        Long utilisateurId = creerUtilisateur("coureur-avec-serie");
        Long traceId = creerTrace(utilisateurId);
        Long serieId = jdbc.queryForObject(
                "INSERT INTO serie (course_trace_id) VALUES (?) RETURNING id",
                Long.class,
                traceId);

        // When
        jdbc.update("DELETE FROM utilisateur WHERE id = ?", utilisateurId);

        // Then
        Long pointeur = jdbc.queryForObject(
                "SELECT course_trace_id FROM serie WHERE id = ?", Long.class, serieId);
        assertThat(pointeur).isNull();
        assertThat(compterTraces(traceId)).isZero();
    }

    private Long creerUtilisateur(String username) {
        return jdbc.queryForObject(
                "INSERT INTO utilisateur (username, password) VALUES (?, ?) RETURNING id",
                Long.class,
                username,
                "x");
    }

    private Long creerTrace(Long utilisateurId) {
        return jdbc.queryForObject(
                """
                        INSERT INTO course_trace
                            (utilisateur_id, points, nb_points, distance_m, duree_s,
                             denivele_positif_m, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                utilisateurId,
                "[]",
                0,
                0.0,
                0,
                0.0,
                LocalDateTime.now());
    }

    private Integer compterTraces(Long traceId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM course_trace WHERE id = ?", Integer.class, traceId);
    }
}
