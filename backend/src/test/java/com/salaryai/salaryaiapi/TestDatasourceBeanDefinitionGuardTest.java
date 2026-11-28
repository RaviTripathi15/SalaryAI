package com.salaryai.salaryaiapi;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.xa.PGXADataSource;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JndiDataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class TestDatasourceBeanDefinitionGuardTest {

    private static final AtomicBoolean SUSPICIOUS_DATA_SOURCE_INSTANTIATED = new AtomicBoolean();
    private static final AtomicBoolean SUSPICIOUS_CONNECTION_DETAILS_INSTANTIATED =
            new AtomicBoolean();
    private static final AtomicBoolean HIDDEN_DATA_SOURCE_INSTANTIATED = new AtomicBoolean();
    private static final AtomicBoolean HIDDEN_CONNECTION_DETAILS_INSTANTIATED =
            new AtomicBoolean();
    private static final String HIDDEN_FACTORY_SECRET =
            "do-not-disclose-" + UUID.randomUUID();

    @BeforeEach
    void resetInstantiationFlags() {
        SUSPICIOUS_DATA_SOURCE_INSTANTIATED.set(false);
        SUSPICIOUS_CONNECTION_DETAILS_INSTANTIATED.set(false);
        HIDDEN_DATA_SOURCE_INSTANTIATED.set(false);
        HIDDEN_CONNECTION_DETAILS_INSTANTIATED.set(false);
    }

    @Test
    void acceptsValidBootManagedTestDataSourceDefinitions() {
        runner().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(DataSource.class)).isExactlyInstanceOf(HikariDataSource.class);
        });
    }

    @Test
    void rejectsCustomDataSourceDefinitionBeforeInstantiation() {
        AtomicBoolean instantiated = new AtomicBoolean();

        Throwable failure = startupFailure(runner()
                .withBean("customDataSource", DataSource.class, () -> {
                    instantiated.set(true);
                    return new SingleConnectionDataSource();
                }));

        assertThat(messages(failure))
                .contains("custom DataSource definition 'customDataSource'");
        assertThat(instantiated).isFalse();
    }

    @Test
    void rejectsCustomHikariDataSourceDefinitionBeforeInstantiation() {
        AtomicBoolean instantiated = new AtomicBoolean();

        Throwable failure = startupFailure(runner()
                .withBean("customHikari", HikariDataSource.class, () -> {
                    instantiated.set(true);
                    return new HikariDataSource();
                }));

        assertThat(messages(failure))
                .contains("custom HikariDataSource definition 'customHikari'");
        assertThat(instantiated).isFalse();
    }

    @Test
    void rejectsXaDataSourceDefinitionBeforeInstantiation() {
        AtomicBoolean instantiated = new AtomicBoolean();

        Throwable failure = startupFailure(runner()
                .withBean("customXaDataSource", PGXADataSource.class, () -> {
                    instantiated.set(true);
                    return new PGXADataSource();
                }));

        assertThat(messages(failure))
                .contains("XADataSource definition 'customXaDataSource'");
        assertThat(instantiated).isFalse();
    }

    @Test
    void rejectsCustomJdbcConnectionDetailsDefinitionBeforeInstantiationWithoutPasswordDisclosure() {
        String secret = "do-not-disclose-" + UUID.randomUUID();
        AtomicBoolean instantiated = new AtomicBoolean();

        Throwable failure = startupFailure(runner()
                .withBean("customConnectionDetails", JdbcConnectionDetails.class,
                        () -> {
                            instantiated.set(true);
                            return new SecretConnectionDetails(secret);
                        }));

        assertThat(messages(failure))
                .contains("custom JdbcConnectionDetails definition 'customConnectionDetails'")
                .doesNotContain(secret);
        assertThat(instantiated).isFalse();
    }

    @Test
    void rejectsJndiDataSourceDefinitionBeforeJndiLookup() {
        ApplicationContextRunner jndiRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JndiDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class))
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withPropertyValues(
                        "spring.datasource.jndi-name=java:comp/env/jdbc/probe",
                        "spring.datasource.url=" + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        "spring.datasource.username="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                        "spring.datasource.driver-class-name="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);

        Throwable failure = startupFailure(jndiRunner);

        assertThat(messages(failure))
                .contains("DataSource definition 'dataSource'")
                .contains("not permitted");
    }

    @Test
    void rejectsAlternativePoolDefinition() {
        Throwable failure = startupFailure(runner()
                .withPropertyValues("spring.datasource.type="
                        + SingleConnectionDataSource.class.getName()));

        assertThat(messages(failure))
                .contains("DataSource definition 'dataSource'")
                .contains("not permitted");
    }

    @Test
    void rejectsUnclassifiableDataSourceDefinition() {
        ApplicationContextRunner unclassifiableRunner = new ApplicationContextRunner()
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withInitializer(context -> {
                    GenericBeanDefinition definition = new GenericBeanDefinition();
                    ((BeanDefinitionRegistry) context).registerBeanDefinition("dataSource", definition);
                });

        Throwable failure = startupFailure(unclassifiableRunner);

        assertThat(messages(failure)).contains("unclassifiable datasource-related definition 'dataSource'");
    }

    @Test
    void rejectsSuspiciousDataSourceFactoryMethodWithUnrelatedDeclaredReturnType() {
        Throwable failure = startupFailure(new ApplicationContextRunner()
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withUserConfiguration(HiddenObjectFactoryConfig.class));

        assertThat(messages(failure))
                .contains("suspicious datasource-related definition 'suspiciousDataSource'")
                .contains("declared metadata does not prove the runtime type");
        assertThat(SUSPICIOUS_DATA_SOURCE_INSTANTIATED).isFalse();
    }

    @Test
    void rejectsSuspiciousJdbcConnectionDetailsFactoryMethodWithUnrelatedDeclaredReturnType() {
        Throwable failure = startupFailure(new ApplicationContextRunner()
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withUserConfiguration(HiddenDetailsFactoryConfig.class));

        assertThat(messages(failure))
                .contains("suspicious datasource-related definition 'suspiciousConnectionDetails'")
                .contains("declared metadata does not prove the runtime type")
                .doesNotContain(HIDDEN_FACTORY_SECRET);
        assertThat(SUSPICIOUS_CONNECTION_DETAILS_INSTANTIATED).isFalse();
    }

    @Test
    void rejectsHiddenRuntimeDataSourceCandidateWithInnocuousFactoryMethodName() {
        Throwable failure = startupFailure(runner().withBean(
                "plainHelper", Object.class,
                () -> {
                    HIDDEN_DATA_SOURCE_INSTANTIATED.set(true);
                    return new HikariDataSource();
                },
                definition -> definition.setPrimary(true)));

        assertThat(messages(failure)).contains("hidden datasource-related bean");
        assertThat(HIDDEN_DATA_SOURCE_INSTANTIATED).isTrue();
    }

    @Test
    void rejectsHiddenRuntimeJdbcConnectionDetailsCandidateWithInnocuousFactoryMethodName() {
        String secret = "do-not-disclose-" + UUID.randomUUID();

        Throwable failure = startupFailure(runner().withBean(
                "plainHelperDetails", Object.class,
                () -> {
                    HIDDEN_CONNECTION_DETAILS_INSTANTIATED.set(true);
                    return new SecretConnectionDetails(secret);
                }));

        assertThat(messages(failure))
                .contains("hidden datasource-related bean")
                .doesNotContain(secret);
        assertThat(HIDDEN_CONNECTION_DETAILS_INSTANTIATED).isTrue();
    }

    @Test
    void rejectsFinalHikariUrlMutationAppliedByBeanPostProcessor() {
        String unsafeUrl = "jdbc:postgresql://127.0.0.1:1/salaryai_late_probe";

        Throwable failure = startupFailure(runner().withBean(
                "urlMutationProcessor", BeanPostProcessor.class,
                () -> new BeanPostProcessor() {
                    @Override
                    public Object postProcessBeforeInitialization(Object bean, String beanName) {
                        if (TestDatasourceConstants.DATASOURCE_BEAN_NAME.equals(beanName)
                                && bean instanceof HikariDataSource dataSource) {
                            dataSource.setJdbcUrl(unsafeUrl);
                        }
                        return bean;
                    }
                }));

        assertThat(messages(failure))
                .contains("jdbcUrl")
                .contains(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL)
                .doesNotContain(unsafeUrl);
    }

    @Test
    void rejectsDataSourceWrappedOrReplacedByBeanPostProcessor() {
        Throwable failure = startupFailure(runner().withBean(
                "wrappingProcessor", BeanPostProcessor.class,
                () -> new BeanPostProcessor() {
                    @Override
                    public Object postProcessAfterInitialization(Object bean, String beanName) {
                        if (!TestDatasourceConstants.DATASOURCE_BEAN_NAME.equals(beanName)) {
                            return bean;
                        }
                        HikariDataSource replacement = new HikariDataSource();
                        replacement.setJdbcUrl(
                                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
                        replacement.setUsername(
                                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
                        replacement.setDriverClassName(
                                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
                        return replacement;
                    }
                }));

        assertThat(messages(failure)).contains("wrapped or replaced by a BeanPostProcessor");
    }

    @Test
    void rejectsSuspiciousDataSourceAlias() {
        ApplicationContextRunner aliasRunner = new ApplicationContextRunner()
                .withInitializer(context -> ((BeanDefinitionRegistry) context)
                        .registerAlias("dataSource", "aliasToDataSource"))
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        "spring.datasource.username="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                        "spring.datasource.driver-class-name="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);

        Throwable failure = startupFailure(aliasRunner);

        assertThat(messages(failure))
                .contains("custom HikariDataSource definition 'dataSource'")
                .contains("not permitted");
    }

    @Test
    void rejectsReplacedVerifierDefinition() {
        ApplicationContextRunner replacedRunner = new ApplicationContextRunner()
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withInitializer(context -> {
                    BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context;
                    registry.removeBeanDefinition(
                            TestDatasourceGuardApplicationContextInitializer.VERIFIER_BEAN_NAME);
                    GenericBeanDefinition replacement = new GenericBeanDefinition();
                    replacement.setBeanClassName(Object.class.getName());
                    registry.registerBeanDefinition(
                            TestDatasourceGuardApplicationContextInitializer.VERIFIER_BEAN_NAME,
                            replacement);
                })
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        "spring.datasource.username="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                        "spring.datasource.driver-class-name="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);

        Throwable failure = startupFailure(replacedRunner);

        assertThat(messages(failure))
                .contains("test datasource verifier definition is missing, replaced, or duplicated");
    }

    @Test
    void rejectsLateMergedBeanDefinitionPostProcessorThatCouldMutateFinalDatasource() {
        Throwable failure = startupFailure(runner().withBean(
                "lateMutatingProcessor", MergedBeanDefinitionPostProcessor.class,
                () -> (beanDefinition, beanType, beanName) -> {
                    // Guard must reject the definition before this processor can be
                    // re-registered after the final verifier.
                }));

        assertThat(messages(failure))
                .contains("custom MergedBeanDefinitionPostProcessor definition "
                        + "'lateMutatingProcessor'");
    }

    @Test
    void rejectsMergedBeanDefinitionPostProcessorRegisteredByLaterBeanFactoryPostProcessor() {
        Throwable failure = startupFailure(runner().withBean(
                "lateProcessorRegistrar", BeanFactoryPostProcessor.class,
                () -> beanFactory -> {
                    GenericBeanDefinition definition = new GenericBeanDefinition();
                    definition.setBeanClass(MergedBeanDefinitionPostProcessor.class);
                    definition.setInstanceSupplier(
                            () -> (MergedBeanDefinitionPostProcessor)
                                    (beanDefinition, beanType, beanName) -> { });
                    ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(
                            "lateRegisteredProcessor", definition);
                }));

        assertThat(messages(failure))
                .contains("custom MergedBeanDefinitionPostProcessor definition "
                        + "'lateRegisteredProcessor'");
    }

    @Test
    void rejectsSubclassOfFrameworkMergedBeanDefinitionPostProcessor() {
        Throwable failure = startupFailure(runner().withInitializer(context -> {
            GenericBeanDefinition definition = new GenericBeanDefinition();
            definition.setBeanClass(CustomAutowiredProcessor.class);
            definition.setInstanceSupplier(CustomAutowiredProcessor::new);
            ((BeanDefinitionRegistry) context).registerBeanDefinition(
                    "trustedProcessorSubclass", definition);
        }));

        assertThat(messages(failure))
                .contains("custom MergedBeanDefinitionPostProcessor definition "
                        + "'trustedProcessorSubclass'");
    }

    @Test
    void rejectsVerifierDefinitionRemovedByLaterBeanFactoryPostProcessor() {
        Throwable failure = startupFailure(runner().withBean(
                "verifierRemover", BeanFactoryPostProcessor.class,
                () -> beanFactory -> ((BeanDefinitionRegistry) beanFactory)
                        .removeBeanDefinition(
                                TestDatasourceGuardApplicationContextInitializer.VERIFIER_BEAN_NAME)));

        assertThat(messages(failure))
                .contains("test datasource verifier definition was removed");
    }

    @Test
    void allowsDatasourceFreeSpringContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmptyConfiguration.class)
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void rejectsDataSourceDefinitionAddedByLaterBeanFactoryPostProcessorBeforeInstantiation() {
        AtomicBoolean instantiated = new AtomicBoolean();
        ApplicationContextRunner lateRunner = new ApplicationContextRunner()
                .withUserConfiguration(EmptyConfiguration.class)
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withBean("lateDataSourceRegistrar", BeanFactoryPostProcessor.class,
                        () -> beanFactory -> {
                            GenericBeanDefinition definition = new GenericBeanDefinition();
                            definition.setBeanClass(SingleConnectionDataSource.class);
                            definition.setInstanceSupplier(() -> {
                                instantiated.set(true);
                                return new SingleConnectionDataSource();
                            });
                            ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(
                                    "lateDataSource", definition);
                        });

        Throwable failure = startupFailure(lateRunner);

        assertThat(messages(failure))
                .contains("custom DataSource definition 'lateDataSource'");
        assertThat(instantiated).isFalse();
    }

    @Test
    void finalStateRecheckRejectsLiveDataSourceSingletonReplacement() {
        HikariDataSource original = canonicalHikariDataSource();
        HikariDataSource replacement = canonicalHikariDataSource();
        TestDatasourceRuntimeVerifier verifier = new TestDatasourceRuntimeVerifier();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("dataSource",
                new RootBeanDefinition(HikariDataSource.class));
        try {
            Class<?> detailsClass = Class.forName(
                    TestDatasourceConstants.PROPERTIES_CONNECTION_DETAILS_CLASS_NAME);
            beanFactory.registerBeanDefinition("jdbcConnectionDetails",
                    new RootBeanDefinition(detailsClass));
        }
        catch (ClassNotFoundException exception) {
            throw new AssertionError("Boot connection-details class is unavailable", exception);
        }
        beanFactory.registerBeanDefinition("testDatasourceRuntimeVerifier",
                new RootBeanDefinition(TestDatasourceRuntimeVerifier.class));
        beanFactory.registerSingleton("dataSource", replacement);
        verifier.setBeanFactory(beanFactory);
        verifier.postProcessAfterInitialization(original, "dataSource");

        IllegalStateException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> verifier.verifyFinalState("laterBean"), IllegalStateException.class);

        assertThat(failure)
                .isNotNull()
                .hasMessageContaining("singleton was replaced");
    }

    @Test
    void rejectsJndiDataSourceFactoryClassNameWithTypoFixedDetection() {
        ApplicationContextRunner jndiNameRunner = new ApplicationContextRunner()
                .withInitializer(context -> {
                    GenericBeanDefinition definition = new GenericBeanDefinition();
                    definition.setBeanClassName("com.example.JndiDataSourceFactory");
                    ((BeanDefinitionRegistry) context)
                            .registerBeanDefinition("plainFactoryBean", definition);
                })
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()));

        Throwable failure = startupFailure(jndiNameRunner);

        assertThat(messages(failure))
                .contains("unclassifiable datasource-related definition 'plainFactoryBean'")
                .contains("unresolvable datasource-related definition 'plainFactoryBean'");
    }

    @Test
    void runtimeVerifierDoesNotReadPasswordsOrDiscloseThem() {
        String secret = "do-not-disclose-" + UUID.randomUUID();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/salaryai_db");
        dataSource.setUsername(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        dataSource.setDriverClassName(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
        dataSource.setPassword(secret);

        IllegalStateException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> new TestDatasourceRuntimeVerifier()
                        .postProcessAfterInitialization(dataSource, "dataSource"),
                IllegalStateException.class);

        assertThat(failure).isNotNull();
        assertThat(failure.getMessage())
                .contains("jdbcUrl")
                .contains(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL)
                .doesNotContain("salaryai_db")
                .doesNotContain(secret);
    }

    @Test
    void runtimeVerifierRejectsUnexpectedImplementationBeforeAnyPasswordAccessor() {
        HikariDataSource subclass = new HikariDataSource() {
            @Override
            public String getPassword() {
                throw new AssertionError("password accessor was called");
            }
        };
        subclass.setJdbcUrl(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        subclass.setUsername(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        subclass.setDriverClassName(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);

        IllegalStateException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> new TestDatasourceRuntimeVerifier()
                        .postProcessBeforeInitialization(subclass, "dataSource"),
                IllegalStateException.class);

        assertThat(failure)
                .isNotNull()
                .hasMessageContaining("unexpected DataSource implementation");
        assertThat(failure.getMessage()).doesNotContain("password accessor was called");
    }

    @Test
    void initialVerificationRejectsAnAlreadyInitializedHikariPool() throws Exception {
        HikariDataSource dataSource = canonicalHikariDataSource();
        setPool(dataSource, mock(HikariPool.class));
        TestDatasourceRuntimeVerifier verifier = verifierWithCandidateDefinitions();

        IllegalStateException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> verifier.postProcessAfterInitialization(dataSource, "dataSource"),
                IllegalStateException.class);

        assertThat(failure)
                .isNotNull()
                .hasMessageContaining("Hikari pool was initialized before runtime verification");
    }

    @Test
    void finalStateRecheckAllowsPoolInitializationAfterSchemaStartup() throws Exception {
        HikariDataSource dataSource = canonicalHikariDataSource();
        setPool(dataSource, mock(HikariPool.class));
        TestDatasourceRuntimeVerifier verifier = verifierWithCandidateDefinitions();
        setVerifiedDataSource(verifier, dataSource);

        assertThatCode(verifier::verifyFinalState).doesNotThrowAnyException();
    }

    @Test
    void finalStateRecheckStillRejectsLateDatasourceConfigurationMutation() throws Exception {
        HikariDataSource dataSource = canonicalHikariDataSource();
        TestDatasourceRuntimeVerifier verifier = verifierWithCandidateDefinitions();
        verifier.postProcessAfterInitialization(dataSource, "dataSource");
        dataSource.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/salaryai_late_probe");

        IllegalStateException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                verifier::verifyFinalState, IllegalStateException.class);

        assertThat(failure)
                .isNotNull()
                .hasMessageContaining("jdbcUrl")
                .hasMessageContaining(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        assertThat(failure.getMessage()).doesNotContain("salaryai_late_probe");
    }

    private static HikariDataSource canonicalHikariDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        dataSource.setUsername(TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        dataSource.setDriverClassName(
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
        return dataSource;
    }

    private static TestDatasourceRuntimeVerifier verifierWithCandidateDefinitions() {
        TestDatasourceRuntimeVerifier verifier = new TestDatasourceRuntimeVerifier();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("dataSource",
                new RootBeanDefinition(HikariDataSource.class));
        try {
            Class<?> detailsClass = Class.forName(
                    TestDatasourceConstants.PROPERTIES_CONNECTION_DETAILS_CLASS_NAME);
            beanFactory.registerBeanDefinition("jdbcConnectionDetails",
                    new RootBeanDefinition(detailsClass));
        }
        catch (ClassNotFoundException exception) {
            throw new AssertionError("Boot connection-details class is unavailable", exception);
        }
        beanFactory.registerBeanDefinition("testDatasourceRuntimeVerifier",
                new RootBeanDefinition(TestDatasourceRuntimeVerifier.class));
        verifier.setBeanFactory(beanFactory);
        return verifier;
    }

    private static void setPool(HikariDataSource dataSource, HikariPool pool) throws Exception {
        Field field = HikariDataSource.class.getDeclaredField("pool");
        field.setAccessible(true);
        field.set(dataSource, pool);
    }

    private static void setVerifiedDataSource(
            TestDatasourceRuntimeVerifier verifier, HikariDataSource dataSource) throws Exception {
        Field field = TestDatasourceRuntimeVerifier.class.getDeclaredField("verifiedDataSource");
        field.setAccessible(true);
        field.set(verifier, dataSource);
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withInitializer(new TestDatasourceGuardApplicationContextInitializer(
                        new TestDatasourceRuntimeVerifier()))
                .withPropertyValues(
                        "spring.datasource.url="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL,
                        "spring.datasource.username="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME,
                        "spring.datasource.driver-class-name="
                                + TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
    }

    private static Throwable startupFailure(ApplicationContextRunner runner) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runner.run(context -> failure.set(context.getStartupFailure()));
        assertThat(failure.get()).as("startup failure").isNotNull();
        return failure.get();
    }

    private static String messages(Throwable failure) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append('\n');
            }
        }
        return result.toString();
    }

    static final class CustomAutowiredProcessor extends AutowiredAnnotationBeanPostProcessor {
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class HiddenObjectFactoryConfig {

        @Bean
        @Primary
        Object suspiciousDataSource() {
            SUSPICIOUS_DATA_SOURCE_INSTANTIATED.set(true);
            return new HikariDataSource();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HiddenDetailsFactoryConfig {

        @Bean
        @Primary
        Object suspiciousConnectionDetails() {
            SUSPICIOUS_CONNECTION_DETAILS_INSTANTIATED.set(true);
            return new SecretConnectionDetails(HIDDEN_FACTORY_SECRET);
        }
    }

    private static final class SecretConnectionDetails implements JdbcConnectionDetails {

        private final String secret;

        private SecretConnectionDetails(String secret) {
            this.secret = secret;
        }

        @Override
        public String getUsername() {
            return TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME;
        }

        @Override
        public String getPassword() {
            return secret;
        }

        @Override
        public String getJdbcUrl() {
            return TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL;
        }
    }
}
