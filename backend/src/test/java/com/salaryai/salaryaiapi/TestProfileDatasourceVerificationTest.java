package com.salaryai.salaryaiapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails fast if a test context resolves anything other than the dedicated
 * PostgreSQL test database. This slice does not start JPA, so it performs no
 * schema generation. Only non-secret datasource identity is printed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TestProfileDatasourceVerificationTest {

    private static final String EXPECTED_URL =
            "jdbc:postgresql://localhost:5432/salaryai_test";
    private static final String EXPECTED_USERNAME = "salaryai_test_user";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void resolvesOnlyTheDedicatedTestDatabase() throws Exception {
        assertThat(environment.getActiveProfiles())
                .as("active Spring profiles")
                .containsExactly("test");
        assertThat(System.getenv("SALARYAI_TEST_DB_PASSWORD"))
                .as("SALARYAI_TEST_DB_PASSWORD")
                .isNotNull();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String url = metadata.getURL();
            String username = metadata.getUserName();

            assertThat(url).as("resolved JDBC URL").isEqualTo(EXPECTED_URL);
            assertThat(username).as("resolved JDBC username")
                    .isEqualTo(EXPECTED_USERNAME);

            // Safe to log: URL and username only, never credentials.
            System.out.println(
                    "Verified PostgreSQL test datasource: url=" + url
                            + ", username=" + username);
        }
    }
}
