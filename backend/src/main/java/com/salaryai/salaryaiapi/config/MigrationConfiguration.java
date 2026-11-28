package com.salaryai.salaryaiapi.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Operator-controlled Flyway activation. Boot's Flyway auto-configuration is
 * excluded globally so no connection-details bean is introduced in tests; this
 * configuration only contributes a Flyway bean when the migration profile is
 * explicitly selected.
 */
@Configuration
@Profile("migration")
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class MigrationConfiguration {

    @Bean(initMethod = "migrate")
    Flyway migrationFlyway(DataSource dataSource, Environment environment) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(environment.getProperty(
                        "spring.flyway.locations", "classpath:db/migration"))
                .schemas(environment.getProperty("spring.flyway.schemas", "public"))
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }
}
