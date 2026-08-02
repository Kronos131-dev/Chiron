package com.kronos.chiron.nutrition.olympusdb;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class OlympusDbConfig implements DisposableBean {

    private HikariDataSource dataSource;

    @Bean(name = "olympusJdbcTemplate")
    public NamedParameterJdbcTemplate olympusJdbcTemplate(
            @Value("${olympus.db.url:jdbc:postgresql://olympus-db:5432/olympus_db}") String url,
            @Value("${olympus.db.username:olympus_user}") String username,
            @Value("${olympus.db.password:}") String password) {

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
