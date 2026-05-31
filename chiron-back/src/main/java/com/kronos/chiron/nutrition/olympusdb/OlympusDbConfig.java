package com.kronos.chiron.nutrition.olympusdb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Datasource secondaire, en LECTURE SEULE, vers la base PostgreSQL d'Olympus.
 *
 * <p>On n'expose volontairement <b>aucun</b> bean de type {@link javax.sql.DataSource} :
 * sinon l'auto-configuration Spring Boot du datasource primaire de Chiron se
 * désactiverait (« {@code @ConditionalOnMissingBean(DataSource.class)} »). On construit
 * donc le pool Hikari à la main et on n'expose que le {@link NamedParameterJdbcTemplate}.</p>
 *
 * <p>Le pool est configuré pour ne PAS faire échouer le démarrage si Olympus est
 * injoignable ({@code initializationFailTimeout = -1}) : la page Statistiques
 * fonctionne alors en mode dégradé (nutrition « non liée »).</p>
 */
@Configuration
public class OlympusDbConfig implements DisposableBean {

    private HikariDataSource dataSource;

    @Bean(name = "olympusJdbcTemplate")
    public NamedParameterJdbcTemplate olympusJdbcTemplate(
            @Value("${olympus.db.url:jdbc:postgresql://olympus-db:5432/olympus_db}") String url,
            @Value("${olympus.db.username:olympus_user}") String username,
            @Value("${olympus.db.password:olympus_password}") String password) {

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setReadOnly(true);
        cfg.setPoolName("olympus-ro-pool");
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(4000);
        cfg.setValidationTimeout(2000);
        cfg.setIdleTimeout(30000);
        // Ne bloque pas et ne fait pas échouer le boot si Olympus est down.
        cfg.setInitializationFailTimeout(-1);

        this.dataSource = new HikariDataSource(cfg);
        return new NamedParameterJdbcTemplate(this.dataSource);
    }

    @Override
    public void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
