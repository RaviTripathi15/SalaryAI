package com.salaryai.salaryaiapi;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.Arrays;
import java.util.Properties;

/**
 * Final test-only datasource verifier.
 *
 * <p>It is registered as a bean-defined {@link MergedBeanDefinitionPostProcessor}
 * with the lowest priority. Spring re-registers merged-definition processors at
 * the end of the BeanPostProcessor chain, so this verifier's
 * {@link #postProcessAfterInitialization(Object, String)} callback runs after
 * Boot's configuration-property binding and Hikari connection-details
 * post-processors have applied their final values, but before singleton
 * consumers such as schema initializers can request a connection.</p>
 *
 * <p>Custom {@code MergedBeanDefinitionPostProcessor} definitions and instances
 * are rejected both by the bean-definition guard and again in
 * {@link #postProcessMergedBeanDefinition(RootBeanDefinition, Class, String)},
 * which Spring invokes after all post-processor registration but before regular
 * bean creation. The uninitialized-pool check is performed only at the initial
 * verification point. A manually registered lifecycle sentinel repeats the
 * non-secret configuration and candidate checks before every later bean
 * initialization, after JPA/schema startup has legitimately initialized the
 * pool. This verifier never calls {@code getBean()}, never reads a password
 * value, never opens JDBC connections, and never starts Hibernate or schema
 * initialization.</p>
 */
public class TestDatasourceRuntimeVerifier
        implements MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {

    private static final String VERIFIER_BEAN_NAME = "testDatasourceRuntimeVerifier";
    private static final String VERIFIER_CLASS_NAME =
            "com.salaryai.salaryaiapi.TestDatasourceRuntimeVerifier";

    private ConfigurableListableBeanFactory beanFactory;
    private Object beforeInitializationBean;
    private Object verifiedDataSource;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory configurable)) {
            throw new IllegalStateException(
                    "Unsafe test datasource verification: unsupported bean factory type");
        }
        this.beanFactory = configurable;
    }

    @Override
    public void postProcessMergedBeanDefinition(
            RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {

        // Spring invokes this callback through the registered merged-definition
        // processor chain after all BeanFactoryPostProcessors and all
        // BeanPostProcessor registrations, but before any regular singleton is
        // created. That is the first point at which definitions added by a late
        // BFPP and manual BPP registrations are both visible. Fail closed if a
        // custom merged-definition processor could be re-registered after this
        // verifier and mutate the final datasource.
        verifyPostProcessorChain();
    }

    private void verifyPostProcessorChain() {
        if (this.beanFactory == null) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: verifier bean factory is unavailable");
        }
        if (!(this.beanFactory instanceof BeanDefinitionRegistry)
                || !TestDatasourceBeanDefinitionGuard.hasDatasourceScope(this.beanFactory)) {
            return;
        }

        for (String name : this.beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = this.beanFactory.getBeanDefinition(name);
            if (VERIFIER_BEAN_NAME.equals(name)
                    || VERIFIER_CLASS_NAME.equals(definition.getBeanClassName())) {
                continue;
            }
            if (TestDatasourceBeanDefinitionGuard.isMergedPostProcessorDefinition(
                    this.beanFactory, name, definition)
                    && !TestDatasourceBeanDefinitionGuard.isAllowedMergedPostProcessor(
                            name, definition)) {
                throw new IllegalStateException(
                        "Unsafe test datasource runtime configuration: custom "
                                + "MergedBeanDefinitionPostProcessor definition '" + name
                                + "' was registered after the bean-definition guard");
            }
        }

        if (this.beanFactory instanceof AbstractBeanFactory abstractBeanFactory) {
            for (BeanPostProcessor postProcessor : abstractBeanFactory.getBeanPostProcessors()) {
                if (postProcessor instanceof MergedBeanDefinitionPostProcessor
                        && !TestDatasourceBeanDefinitionGuard.ALLOWED_MERGED_POST_PROCESSOR_CLASSES
                                .contains(postProcessor.getClass().getName())) {
                    throw new IllegalStateException(
                            "Unsafe test datasource runtime configuration: custom "
                                    + "MergedBeanDefinitionPostProcessor instance (class withheld) "
                                    + "was registered after the bean-definition guard");
                }
            }
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (!TestDatasourceConstants.DATASOURCE_BEAN_NAME.equals(beanName)) {
            return bean;
        }
        this.beforeInitializationBean = bean;
        checkExactClass(bean);
        checkPoolUninitialized((HikariDataSource) bean);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!TestDatasourceConstants.DATASOURCE_BEAN_NAME.equals(beanName)) {
            checkNonPrimaryCandidate(bean, beanName);
            return bean;
        }

        if (this.beforeInitializationBean != null && this.beforeInitializationBean != bean) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: the 'dataSource' bean was "
                            + "wrapped or replaced by a BeanPostProcessor");
        }

        this.verifiedDataSource = bean;
        verifyInitialState();
        return bean;
    }

    /**
     * Verifies the datasource at its initialization point, before any consumer
     * can initialize the Hikari pool or start schema generation.
     */
    public void verifyInitialState() {
        if (this.verifiedDataSource == null) {
            return;
        }
        checkExactClass(this.verifiedDataSource);
        HikariDataSource dataSource = (HikariDataSource) this.verifiedDataSource;
        checkPoolUninitialized(dataSource);
        verifyConfigurationAndCandidates(dataSource);
    }

    /**
     * Repeats the non-secret datasource configuration and candidate checks
     * against the already verified instance. Called by the lifecycle sentinel
     * before each later bean initialization. It deliberately does not require an
     * uninitialized pool because JPA and SQL initialization may have started it.
     */
    public void verifyFinalState() {
        verifyFinalState(null);
    }

    /**
     * Repeats the non-secret checks and, when called from the lifecycle sentinel
     * for a later bean, confirms that the live datasource singleton was not
     * replaced after the initial verification.
     */
    public void verifyFinalState(String currentBeanName) {
        if (this.verifiedDataSource != null
                && currentBeanName != null
                && !TestDatasourceConstants.DATASOURCE_BEAN_NAME.equals(currentBeanName)) {
            checkLiveDataSourceIdentity();
        }
        if (this.verifiedDataSource == null) {
            return;
        }
        checkExactClass(this.verifiedDataSource);
        verifyConfigurationAndCandidates((HikariDataSource) this.verifiedDataSource);
    }

    private void checkLiveDataSourceIdentity() {
        if (this.beanFactory == null
                || !this.beanFactory.containsBeanDefinition(
                        TestDatasourceConstants.DATASOURCE_BEAN_NAME)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: verified 'dataSource' "
                            + "bean definition is unavailable");
        }
        Object live = this.beanFactory.getBean(TestDatasourceConstants.DATASOURCE_BEAN_NAME);
        if (live != this.verifiedDataSource) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: the verified 'dataSource' "
                            + "singleton was replaced (actual instance withheld)");
        }
    }

    private void verifyConfigurationAndCandidates(HikariDataSource dataSource) {
        checkExact("jdbcUrl", dataSource.getJdbcUrl(),
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        checkExact("username", dataSource.getUsername(),
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
        checkExact("driverClassName", dataSource.getDriverClassName(),
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_DRIVER_CLASS_NAME);
        checkBlank("dataSourceClassName", dataSource.getDataSourceClassName());
        checkBlank("dataSourceJNDI", dataSource.getDataSourceJNDI());
        checkBlank("catalog", dataSource.getCatalog());
        checkDriverProperties(dataSource.getDataSourceProperties());
        checkEffectiveCandidates();
    }

    @Override
    public int getOrder() {
        // Highest numeric value within the PriorityOrdered BPP group; the
        // MergedBeanDefinitionPostProcessor re-registration then moves this
        // instance after all regular BeanPostProcessors.
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static void checkExactClass(Object bean) {
        if (!(bean instanceof HikariDataSource dataSource)
                || bean.getClass() != HikariDataSource.class) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: unexpected DataSource implementation "
                            + "(actual class withheld); only com.zaxxer.hikari.HikariDataSource is permitted");
        }
    }

    private static void checkNonPrimaryCandidate(Object bean, String beanName) {
        if (TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME.equals(beanName)) {
            checkExactApprovedConnectionDetails(bean);
            return;
        }
        if (bean instanceof DataSource
                || bean instanceof XADataSource
                || bean instanceof JdbcConnectionDetails) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: hidden datasource-related bean "
                            + "was instantiated (bean name and actual class withheld); only the verified "
                            + "'dataSource' and Boot-managed 'jdbcConnectionDetails' beans are permitted");
        }
    }

    private static void checkExactApprovedConnectionDetails(Object bean) {
        if (bean == null
                || !TestDatasourceConstants.PROPERTIES_CONNECTION_DETAILS_CLASS_NAME
                        .equals(bean.getClass().getName())
                || !(bean instanceof JdbcConnectionDetails details)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: unexpected JdbcConnectionDetails "
                            + "implementation (actual class withheld); only Boot-managed "
                            + "PropertiesJdbcConnectionDetails is permitted");
        }
        checkExact("jdbcConnectionDetails.jdbcUrl", details.getJdbcUrl(),
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_URL);
        checkExact("jdbcConnectionDetails.username", details.getUsername(),
                TestDatasourceGuardEnvironmentPostProcessor.EXPECTED_USERNAME);
    }

    private static void checkPoolUninitialized(HikariDataSource dataSource) {
        if (dataSource.getHikariPoolMXBean() != null) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: Hikari pool was initialized "
                            + "before runtime verification");
        }
    }

    private static void checkExact(String property, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: " + property
                            + " must be exactly " + expected
                            + " (missing or different; actual value withheld)");
        }
    }

    private static void checkBlank(String property, String actual) {
        if (actual != null && !actual.isBlank()) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: " + property
                            + " must be empty (actual value withheld)");
        }
    }

    private static void checkDriverProperties(Properties properties) {
        if (!properties.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: Hikari driver properties must "
                            + "be empty (first property name withheld)");
        }
    }

    private void checkEffectiveCandidates() {
        if (this.beanFactory == null) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: verifier bean factory is unavailable");
        }

        String[] dataSourceNames =
                this.beanFactory.getBeanNamesForType(DataSource.class, true, false);
        String[] xaDataSourceNames =
                this.beanFactory.getBeanNamesForType(XADataSource.class, true, false);
        String[] connectionDetailsNames =
                this.beanFactory.getBeanNamesForType(JdbcConnectionDetails.class, true, false);

        if (!Arrays.equals(new String[] {
                TestDatasourceConstants.DATASOURCE_BEAN_NAME}, dataSourceNames)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: expected exactly one effective "
                            + "DataSource bean named 'dataSource' (actual bean names withheld)");
        }
        if (xaDataSourceNames.length != 0) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: XADataSource beans are not permitted");
        }
        if (!Arrays.equals(new String[] {
                TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME}, connectionDetailsNames)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: expected exactly one effective "
                            + "JdbcConnectionDetails bean named 'jdbcConnectionDetails' "
                            + "(actual bean names withheld)");
        }

        for (String name : dataSourceNames) {
            BeanDefinition definition = this.beanFactory.getBeanDefinition(name);
            if (definition.isPrimary()) {
                throw new IllegalStateException(
                        "Unsafe test datasource runtime configuration: unexpected @Primary datasource candidate '"
                                + name + "'");
            }
        }

        String[] aliases = this.beanFactory.getAliases(TestDatasourceConstants.DATASOURCE_BEAN_NAME);
        if (aliases.length != 0) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: the verified 'dataSource' bean "
                            + "has unexpected aliases (actual aliases withheld)");
        }

        if (!this.beanFactory.containsBeanDefinition(VERIFIER_BEAN_NAME)) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: verifier definition was removed");
        }
        BeanDefinition verifierDefinition =
                this.beanFactory.getBeanDefinition(VERIFIER_BEAN_NAME);
        if (!TestDatasourceRuntimeVerifier.class.getName()
                .equals(verifierDefinition.getBeanClassName())) {
            throw new IllegalStateException(
                    "Unsafe test datasource runtime configuration: verifier definition was replaced");
        }
    }
}
