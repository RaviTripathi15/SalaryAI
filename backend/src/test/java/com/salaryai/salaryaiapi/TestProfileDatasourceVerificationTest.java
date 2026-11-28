package com.salaryai.salaryaiapi;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the datasource actually created by Spring Boot is the isolated
 * PostgreSQL test datasource. Verification uses non-secret Hikari configuration
 * only: no JDBC connection, schema operation or password access is performed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TestProfileDatasourceVerificationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void resolvesOnlyTheDedicatedTestDatabaseWithoutConnecting() {
        assertThat(environment.getActiveProfiles())
                .as("active Spring profiles")
                .containsExactly("test");
        assertThat(dataSource)
                .as("datasource implementation")
                .isExactlyInstanceOf(HikariDataSource.class);

        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getJdbcUrl())
                .as("resolved JDBC URL")
                .isEqualTo(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        assertThat(hikari.getUsername())
                .as("resolved JDBC username")
                .isEqualTo(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        assertThat(hikari.getDriverClassName())
                .as("resolved JDBC driver")
                .isEqualTo(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
        assertThat(hikari.getHikariPoolMXBean())
                .as("Hikari pool")
                .isNull();
    }
}
