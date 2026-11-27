package com.salaryai.salaryaiapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TestDatasourceGuardEnvironmentPostProcessorTest {

    private static final String URL_NAME = "spring.datasource.url";
    private static final String USERNAME_NAME = "spring.datasource.username";
    private static final String PASSWORD_NAME = "spring.datasource.password";
    private static final String DRIVER_NAME = "spring.datasource.driver-class-name";

    private final TestDatasourceGuardEnvironmentPostProcessor guard =
            new TestDatasourceGuardEnvironmentPostProcessor();

    @Test
    void acceptsTheExpectedTestDatasource() {
        MockEnvironment environment = canonicalEnvironment();

        assertThatCode(() -> guard.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsTheExpectedDriverClassName() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty(DRIVER_NAME,
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);

        assertThatCode(() -> guard.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsValidConfigurationWithAttachedConfigurationPropertySourceWithoutNullPointerException() {
        MockEnvironment environment = canonicalEnvironment();
        ConfigurationPropertySources.attach(environment);

        assertThatCode(() -> guard.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeEnumerablePropertyWithAttachedConfigurationPropertySource() {
        MockEnvironment environment = canonicalEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "forbidden-lower-priority",
                Map.of("spring.datasource.hikari.jdbc-url", "hidden-probe-value")));
        ConfigurationPropertySources.attach(environment);

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.hikari.jdbc-url")
                .doesNotContain("hidden-probe-value");
    }

    @Test
    void rejectsIncorrectUrlWithoutDisclosingTheActualValue() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty(URL_NAME,
                "jdbc:postgresql://localhost:5432/salaryai_db");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains(URL_NAME)
                .contains(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL)
                .doesNotContain("salaryai_db")
                .doesNotContain("password");
    }

    @Test
    void rejectsIncorrectUsernameWithoutDisclosingTheActualValue() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty(USERNAME_NAME, "unexpected_user");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains(USERNAME_NAME)
                .contains(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME)
                .doesNotContain("unexpected_user")
                .doesNotContain("password");
    }

    @Test
    void rejectsMissingDatasourceValues() {
        IllegalStateException failure = reject(new MockEnvironment());

        assertThat(failure.getMessage())
                .contains(URL_NAME)
                .contains(USERNAME_NAME);
    }

    @Test
    void rejectsIncorrectDriverClassNameWithoutDisclosingTheActualValue() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty(DRIVER_NAME, "unexpected.Driver");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains(DRIVER_NAME)
                .contains(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME)
                .doesNotContain("unexpected.Driver");
    }

    @Test
    void rejectsHikariJdbcUrlOverride() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty("spring.datasource.hikari.jdbc-url",
                "jdbc:postgresql://127.0.0.1:1/salaryai_hikari_probe");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.hikari.jdbc-url")
                .doesNotContain("salaryai_hikari_probe");
    }

    @Test
    void rejectsHikariUsernameOverride() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty("spring.datasource.hikari.username", "hidden_user");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.hikari.username")
                .doesNotContain("hidden_user");
    }

    @Test
    void rejectsHikariDataSourceProperties() {
        List<String> keys = List.of(
                "spring.datasource.hikari.data-source-properties.url",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-j-n-d-i");

        for (String key : keys) {
            MockEnvironment environment = canonicalEnvironment();
            environment.setProperty(key, "hidden-probe-value");

            IllegalStateException failure = reject(environment);

            assertThat(failure.getMessage())
                    .as("rejected %s", key)
                    .contains(key)
                    .doesNotContain("hidden-probe-value");
        }
    }

    @Test
    void rejectsJndiDatasource() {
        MockEnvironment environment = canonicalEnvironment();
        environment.setProperty("spring.datasource.jndi-name", "java:comp/env/jdbc/probe");

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.jndi-name")
                .doesNotContain("java:comp/env/jdbc/probe");
    }

    @Test
    void rejectsAlternativePoolAndXaProperties() {
        List<String> keys = List.of(
                "spring.datasource.tomcat.url",
                "spring.datasource.dbcp2.username",
                "spring.datasource.oracleucp.u-r-l",
                "spring.datasource.xa.data-source-class-name",
                "spring.datasource.type");

        for (String key : keys) {
            MockEnvironment environment = canonicalEnvironment();
            environment.setProperty(key, "hidden-probe-value");

            IllegalStateException failure = reject(environment);

            assertThat(failure.getMessage())
                    .as("rejected %s", key)
                    .contains(key)
                    .doesNotContain("hidden-probe-value");
        }
    }

    @Test
    void rejectsEnvironmentVariableNamingVariants() {
        MockEnvironment environment = canonicalEnvironment();
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                "test-system-environment",
                Map.of(
                        "SPRING_DATASOURCE_HIKARI_JDBCURL", "hidden-probe-value",
                        "SPRING_DATASOURCE_HIKARI_USERNAME", "hidden-probe-value",
                        "SPRING_DATASOURCE_JNDI_NAME", "hidden-probe-value")));

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.hikari.jdbcurl")
                .contains("spring.datasource.hikari.username")
                .contains("spring.datasource.jndi.name")
                .doesNotContain("hidden-probe-value");
    }

    @Test
    void rejectsForbiddenLowerPriorityPropertyEvenWhenHigherPriorityValueWins() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "high-priority-safe",
                Map.of(URL_NAME, TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        USERNAME_NAME, TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME)));
        environment.getPropertySources().addLast(new MapPropertySource(
                "low-priority-forbidden",
                Map.of("spring.datasource.hikari.jdbc-url", "hidden-probe-value")));

        IllegalStateException failure = reject(environment);

        assertThat(failure.getMessage())
                .contains("spring.datasource.hikari.jdbc-url")
                .doesNotContain("hidden-probe-value");
    }

    @Test
    void passwordValueIsNeverRetrievedOrDisclosed() {
        String passwordValue = "do-not-disclose-" + UUID.randomUUID();
        CountingMockEnvironment environment = new CountingMockEnvironment();
        environment.setProperty(URL_NAME,
                "jdbc:postgresql://127.0.0.1:1/salaryai_password_probe");
        environment.setProperty(USERNAME_NAME,
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        environment.setProperty(PASSWORD_NAME, passwordValue);
        environment.clearReadKeys();

        IllegalStateException failure = reject(environment);

        assertThat(environment.getReadKeys()).doesNotContain(PASSWORD_NAME);
        assertThat(failure.getMessage()).doesNotContain(passwordValue);
    }

    @Test
    @SuppressWarnings("deprecation")
    void isRegisteredForAllSpringTestContexts() {
        List<String> registered = SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class, getClass().getClassLoader());

        assertThat(registered).contains(guard.getClass().getName());
    }

    @Test
    void runsAfterConfigDataProcessing() {
        assertThat(guard.getOrder())
                .isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    }

    private static MockEnvironment canonicalEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(URL_NAME,
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        environment.setProperty(USERNAME_NAME,
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        return environment;
    }

    private IllegalStateException reject(MockEnvironment environment) {
        IllegalStateException failure = catchThrowableOfType(
                () -> guard.postProcessEnvironment(environment, null),
                IllegalStateException.class);
        assertThat(failure).isNotNull();
        return failure;
    }

    private static final class CountingMockEnvironment extends MockEnvironment {

        private final List<String> readKeys = new ArrayList<>();

        @Override
        public String getProperty(String key) {
            readKeys.add(key);
            return super.getProperty(key);
        }

        List<String> getReadKeys() {
            return readKeys;
        }

        void clearReadKeys() {
            readKeys.clear();
        }
    }
}
