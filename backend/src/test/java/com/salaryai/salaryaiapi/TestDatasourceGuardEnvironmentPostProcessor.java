package com.salaryai.salaryaiapi;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test-only fail-fast guard. It runs after Spring Boot config data has been
 * applied, but before any DataSource or JPA bean is created, so environment
 * variables and command-line overrides are validated rather than trusted.
 *
 * <p>Only the exact test datasource shape is allowed. Every enumerable key in
 * the {@code spring.datasource.*} namespace is checked, and any key that does
 * not map to an approved Spring Boot configuration property is rejected. The
 * password key is allowed by name only; its value is never retrieved, logged,
 * or included in an exception message.</p>
 *
 * <p>Non-enumerable property sources are rejected unless they are Boot's known
 * attached aggregate {@code ConfigurationPropertySourcesPropertySource},
 * {@code RandomValuePropertySource}, or the empty servlet stub present before a
 * web context exists. The aggregate is derived from sources already inspected
 * here; the random source only serves {@code random.*} keys; the stub cannot
 * hold a value. All other opaque sources are rejected.</p>
 *
 * <p>The password is accepted only as the literal
 * {@code ${SALARYAI_TEST_DB_PASSWORD}} placeholder in the test profile's
 * configuration resource. Its resolved value is never retrieved, compared,
 * logged, or included in an exception message.</p>
 */
public final class TestDatasourceGuardEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    static final String EXPECTED_URL =
            "jdbc:postgresql://localhost:5432/salaryai_test";
    static final String EXPECTED_USERNAME = "salaryai_test_user";
    static final String EXPECTED_DRIVER_CLASS_NAME = "org.postgresql.Driver";

    /**
     * ConfigDataEnvironmentPostProcessor.ORDER is HIGHEST_PRECEDENCE + 10;
     * this guard must run after it but still before bean initialization.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private static final String DATASOURCE_PREFIX = "spring.datasource";
    private static final String ATTACHED_CONFIGURATION_PROPERTY_SOURCE_CLASS =
            "org.springframework.boot.context.properties.source.ConfigurationPropertySourcesPropertySource";
    private static final String RANDOM_PROPERTY_SOURCE_CLASS =
            "org.springframework.boot.env.RandomValuePropertySource";
    private static final String EMPTY_STUB_PROPERTY_SOURCE_CLASS =
            "org.springframework.core.env.PropertySource$StubPropertySource";
    private static final String URL_NAME = "spring.datasource.url";
    private static final String USERNAME_NAME = "spring.datasource.username";
    static final String PASSWORD_NAME = "spring.datasource.password";
    private static final String DRIVER_CLASS_NAME = "spring.datasource.driver-class-name";

    static final String PASSWORD_PLACEHOLDER = "${SALARYAI_TEST_DB_PASSWORD}";
    static final String TEST_PROFILE_CONFIG_SOURCE_NAME =
            "Config resource 'class path resource [application-test.properties]' "
                    + "via location 'optional:classpath:/'";

    private static final ConfigurationPropertyName URL_PROPERTY =
            ConfigurationPropertyName.of(URL_NAME);
    private static final ConfigurationPropertyName USERNAME_PROPERTY =
            ConfigurationPropertyName.of(USERNAME_NAME);
    private static final ConfigurationPropertyName PASSWORD_PROPERTY =
            ConfigurationPropertyName.of(PASSWORD_NAME);
    private static final ConfigurationPropertyName DRIVER_CLASS_PROPERTY =
            ConfigurationPropertyName.of(DRIVER_CLASS_NAME);

    private static final List<ConfigurationPropertyName> ALLOWED_PROPERTIES = List.of(
            URL_PROPERTY,
            USERNAME_PROPERTY,
            PASSWORD_PROPERTY,
            DRIVER_CLASS_PROPERTY);

    /**
     * Marker value used when asking Spring Boot's property mapper whether a
     * raw key maps to an allowed property name. The real property value is
     * deliberately not read.
     */
    private static final Object PRESENT = new Object();

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {

        validateEnvironment(environment);
    }

    /**
     * Re-runs the strict datasource checks. The application-context initializer
     * calls this after other initializers have contributed property sources, so
     * a late {@code @DynamicPropertySource} or custom initializer cannot inject
     * unsafe datasource configuration after environment post-processing.
     */
    public void validateEnvironment(ConfigurableEnvironment environment) {

        Set<String> problems = new TreeSet<>();

        validateAllDatasourceKeys(environment, problems);
        validateEffectiveValues(environment, problems);
        validatePerSourceAllowedValues(environment, problems);
        validatePasswordOrigin(environment, problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe test datasource configuration: " + String.join("; ", problems)
                            + ". Tests may only run against the isolated PostgreSQL "
                            + "test database.");
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Checks every enumerable key, not only the winning value, so a forbidden
     * low-priority property cannot hide behind a safe high-priority override.
     */
    private static void validateAllDatasourceKeys(
            ConfigurableEnvironment environment, Set<String> problems) {

        Set<String> inspected = new HashSet<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                if (!isKnownSafeNonEnumerableSource(propertySource)) {
                    problems.add("uninspectable property source '" + propertySource.getName()
                            + "' may contribute datasource configuration");
                }
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                if (!isDatasourceKey(key)) {
                    continue;
                }
                if (!inspected.add(propertySource.getName() + '\0' + key)) {
                    continue;
                }
                if (!mapsToAllowedProperty(propertySource, key)) {
                    problems.add("forbidden datasource property '"
                            + displayName(key) + "'");
                }
            }
        }
    }

    /** Keeps the exact effective-value checks from the previous guard. */
    private static void validateEffectiveValues(
            ConfigurableEnvironment environment, Set<String> problems) {

        addExactValueProblem(problems, URL_NAME,
                environment.getProperty(URL_NAME), EXPECTED_URL);
        addExactValueProblem(problems, USERNAME_NAME,
                environment.getProperty(USERNAME_NAME), EXPECTED_USERNAME);
    }

    /**
     * Rejects a wrong value in any source, even when a higher-priority source
     * supplies the correct effective value. The password source is never
     * queried.
     */
    private static void validatePerSourceAllowedValues(
            ConfigurableEnvironment environment, Set<String> problems) {

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            for (ConfigurationPropertySource source
                    : configurationPropertySources(propertySource)) {
                checkSourceValue(source, URL_PROPERTY, EXPECTED_URL, problems);
                checkSourceValue(source, USERNAME_PROPERTY, EXPECTED_USERNAME, problems);
                checkSourceValue(source, DRIVER_CLASS_PROPERTY,
                        EXPECTED_DRIVER_CLASS_NAME, problems);
            }
        }
    }

    /**
     * Checks that the password is declared exactly once, only by the test
     * profile configuration resource, and only as the environment-variable
     * placeholder. The raw declaration is read directly from the property
     * source; the resolved password is never requested from the environment.
     */
    private static void validatePasswordOrigin(
            ConfigurableEnvironment environment, Set<String> problems) {

        int passwordDeclarations = 0;
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                if (!mapsToAllowedProperty(propertySource, key, PASSWORD_PROPERTY)) {
                    continue;
                }
                passwordDeclarations++;
                Object raw = propertySource.getProperty(key);
                String rawValue = raw == null ? null : raw.toString();
                if (!TEST_PROFILE_CONFIG_SOURCE_NAME.equals(propertySource.getName())
                        || !PASSWORD_PLACEHOLDER.equals(rawValue)) {
                    problems.add("datasource password must be supplied only by "
                            + "SALARYAI_TEST_DB_PASSWORD through application-test.properties "
                            + "(source and value withheld)");
                }
            }
        }
        if (passwordDeclarations != 1) {
            problems.add("expected exactly one datasource password declaration sourced only "
                    + "from SALARYAI_TEST_DB_PASSWORD");
        }
    }

    /**
     * Spring Boot 4 returns {@code null} from
     * {@link ConfigurationPropertySources#from(PropertySource)} for its attached
     * {@code ConfigurationPropertySourcesPropertySource}, because that source is
     * already an aggregate view of the other environment sources. Those sources
     * are inspected individually by
     * {@link #validateAllDatasourceKeys(ConfigurableEnvironment, Set)} and
     * {@link #validatePerSourceAllowedValues(ConfigurableEnvironment, Set)}, so
     * skipping the aggregate is safe. All other unsupported sources are rejected
     * before reaching this method.
     */
    private static List<ConfigurationPropertySource> configurationPropertySources(
            PropertySource<?> propertySource) {

        List<ConfigurationPropertySource> result = new ArrayList<>();
        for (ConfigurationPropertySource source
                : ConfigurationPropertySources.from(propertySource)) {
            if (source != null) {
                result.add(source);
            }
        }
        return result;
    }

    private static void checkSourceValue(
            ConfigurationPropertySource source,
            ConfigurationPropertyName name,
            String expected,
            Set<String> problems) {

        ConfigurationProperty property = source.getConfigurationProperty(name);
        if (property == null) {
            return;
        }
        Object value = property.getValue();
        String actual = value == null ? null : value.toString();
        if (!expected.equals(actual)) {
            addExactValueProblem(
                    problems, name.toString(), actual, expected);
        }
    }

    private static void addExactValueProblem(
            Set<String> problems, String name, String actual, String expected) {

        if (!expected.equals(actual)) {
            problems.add(name + " must be exactly " + expected
                    + " (missing or different; actual value withheld)");
        }
    }

    private static boolean isDatasourceKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('_', '.');
        return normalized.equals(DATASOURCE_PREFIX)
                || normalized.startsWith(DATASOURCE_PREFIX + ".");
    }

    private static boolean isKnownSafeNonEnumerableSource(PropertySource<?> propertySource) {
        String className = propertySource.getClass().getName();
        return ATTACHED_CONFIGURATION_PROPERTY_SOURCE_CLASS.equals(className)
                || RANDOM_PROPERTY_SOURCE_CLASS.equals(className)
                || EMPTY_STUB_PROPERTY_SOURCE_CLASS.equals(className);
    }

    private static boolean mapsToAllowedProperty(
            PropertySource<?> propertySource, String key) {

        for (ConfigurationPropertyName allowed : ALLOWED_PROPERTIES) {
            if (mapsToAllowedProperty(propertySource, key, allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mapsToAllowedProperty(
            PropertySource<?> propertySource, String key, ConfigurationPropertyName allowed) {

        PropertySource<?> probeSource = propertySource instanceof SystemEnvironmentPropertySource
                ? new SystemEnvironmentPropertySource(
                        propertySource.getName(), Collections.singletonMap(key, PRESENT))
                : new MapPropertySource(
                        propertySource.getName(), Collections.singletonMap(key, PRESENT));

        for (ConfigurationPropertySource source
                : configurationPropertySources(probeSource)) {
            if (source.getConfigurationProperty(allowed) != null) {
                return true;
            }
        }
        return false;
    }

    private static String displayName(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
