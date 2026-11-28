package com.salaryai.salaryaiapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatasourceGuardRegistrationTest {

    @Test
    @SuppressWarnings("deprecation")
    void registersBothEnvironmentPostProcessorAndApplicationContextInitializer() {
        ClassLoader classLoader = getClass().getClassLoader();

        List<String> environmentPostProcessors = SpringFactoriesLoader.loadFactoryNames(
                EnvironmentPostProcessor.class, classLoader);
        List<String> initializers = SpringFactoriesLoader.loadFactoryNames(
                ApplicationContextInitializer.class, classLoader);

        assertThat(environmentPostProcessors)
                .contains(TestDatasourceGuardEnvironmentPostProcessor.class.getName());
        assertThat(initializers)
                .contains(TestDatasourceGuardApplicationContextInitializer.class.getName());
    }

    @Test
    void environmentPostProcessorRunsAfterConfigDataProcessing() {
        assertThat(new TestDatasourceGuardEnvironmentPostProcessor().getOrder())
                .isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    }

    @Test
    void applicationContextInitializerRunsAfterFrameworkInitializers() {
        assertThat(new TestDatasourceGuardApplicationContextInitializer().getOrder())
                .isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void revalidatesEnvironmentAfterLaterInitializersContributePropertySources() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        environment.setProperty("spring.datasource.username",
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        environment.getPropertySources().addLast(new MapPropertySource(
                TestDatasourceGuardEnvironmentPostProcessor.TEST_PROFILE_CONFIG_SOURCE_NAME,
                Map.of(TestDatasourceGuardEnvironmentPostProcessor.PASSWORD_NAME,
                        TestDatasourceGuardEnvironmentPostProcessor.PASSWORD_PLACEHOLDER)));
        String unsafeUrl = "jdbc:postgresql://127.0.0.1:1/salaryai_late_property_probe";
        environment.getPropertySources().addFirst(new MapPropertySource(
                "late-context-customizer",
                Map.of("spring.datasource.hikari.jdbc-url", unsafeUrl)));
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(
                () -> new TestDatasourceGuardApplicationContextInitializer().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.hikari.jdbc-url")
                .hasMessageNotContaining(unsafeUrl);
    }

    @Test
    void runtimeVerifierIsRegisteredAsLateBeanDefinedPostProcessor() {
        TestDatasourceRuntimeVerifier verifier = new TestDatasourceRuntimeVerifier();

        assertThat(verifier)
                .isInstanceOf(MergedBeanDefinitionPostProcessor.class)
                .isInstanceOf(PriorityOrdered.class);
        assertThat(verifier.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
