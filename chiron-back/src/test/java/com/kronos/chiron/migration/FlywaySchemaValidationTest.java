package com.kronos.chiron.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context against a real PostgreSQL container, runs every Flyway
 * migration, then lets Hibernate run its schema validation (ddl-auto=validate).
 *
 * If any @Entity field has no matching column in the migrations (or vice versa),
 * the context fails to start and this test fails — which is exactly the prod crash
 * we want to catch in CI rather than at deploy time.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("schema-it")
@Import(FlywaySchemaValidationTest.MailStubConfig.class)
class FlywaySchemaValidationTest {

    @TestConfiguration
    static class MailStubConfig {
        // EmailService injects JavaMailSender directly, so excluding the autoconfig isn't enough.
        // We never send mail in this test — a no-op sender lets the context boot.
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
        // Take precedence over the base test/resources/application.yml,
        // which disables Flyway and uses ddl-auto=create-drop.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void flywayMigrationsMatchJpaEntities() {
        // Empty body on purpose: the assertion is "the Spring context boots".
        // Flyway applies V0..VN, then Hibernate (ddl-auto=validate) compares
        // the resulting schema against every @Entity.
    }
}
