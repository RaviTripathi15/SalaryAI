package com.salaryai.salaryaiapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves with the actually installed Spring Boot 4.0.8 / Spring Framework
 * 7.0.9 dependencies that the environment guard rejects an unsafe datasource
 * URL before an application context is created.
 */
class TestDatasourceStartupOrderTest {

    private static final String PROBE_SOURCE = "test-unsafe-datasource-probe";
    private static final String UNSAFE_URL =
            "jdbc:postgresql://127.0.0.1:1/salaryai_guard_probe";

    @Test
    void unsafeUrlIsRejectedBeforeApplicationContextCreation() {
        ConfigurableEnvironment environment = canonicalEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(PROBE_SOURCE, Map.of(
                "spring.datasource.url", UNSAFE_URL,
                "spring.datasource.username",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                "spring.datasource.driver-class-name",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME)));
        SpringApplication application = application(environment);
        AtomicBoolean contextCreated = new AtomicBoolean();
        application.setApplicationContextFactory(type -> {
            contextCreated.set(true);
            return new AnnotationConfigApplicationContext();
        });

        try {
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(application::run);
            assertThat(failure)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unsafe test datasource configuration")
                    .hasMessageContaining("spring.datasource.url");
            assertThat(failure.getMessage()).doesNotContain(UNSAFE_URL);
            assertThat(contextCreated).as("application context created").isFalse();
        }
        finally {
            environment.getPropertySources().remove(PROBE_SOURCE);
        }
    }

    @Test
    void validIsolatedDatasourceConfigurationPassesEnvironmentPostProcessing() {
        ConfigurableEnvironment environment = canonicalEnvironment();
        MapPropertySource validSource = new MapPropertySource(PROBE_SOURCE, Map.of(
                "spring.datasource.url",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                "spring.datasource.username",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                "spring.datasource.driver-class-name",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME));
        environment.getPropertySources().addFirst(validSource);
        SpringApplication application = application(environment);
        application.setApplicationContextFactory(type -> {
            throw new ReachedContextCreation();
        });

        try {
            assertThatThrownBy(application::run)
                    .as("failure introduced by the test context factory")
                    .isInstanceOf(ReachedContextCreation.class);
        }
        finally {
            environment.getPropertySources().remove(PROBE_SOURCE);
        }
    }

    private static ConfigurableEnvironment canonicalEnvironment() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                TestDatasourceGuardEnvironmentPostProcessor.TEST_PROFILE_CONFIG_SOURCE_NAME,
                Map.of(
                        "spring.datasource.url",
                        TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        "spring.datasource.username",
                        TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                        TestDatasourceGuardEnvironmentPostProcessor.PASSWORD_NAME,
                        TestDatasourceGuardEnvironmentPostProcessor.PASSWORD_PLACEHOLDER,
                        "spring.datasource.driver-class-name",
                        TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME)));
        return environment;
    }

    private static SpringApplication application(ConfigurableEnvironment environment) {
        SpringApplication application = new SpringApplication(SalaryaiApiApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setEnvironment(environment);
        return application;
    }

    private static final class ReachedContextCreation extends RuntimeException {
    }
}
