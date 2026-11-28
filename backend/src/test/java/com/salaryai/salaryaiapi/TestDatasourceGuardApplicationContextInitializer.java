package com.salaryai.salaryaiapi;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Test-only registration point for the bean-definition guard and the final
 * datasource verifier. Spring Boot applies context initializers before
 * {@link ConfigurableApplicationContext#refresh()}; the bean-factory
 * post-processor added here therefore runs after configuration-class
 * processing and before datasource singleton creation.
 *
 * <p>The verifier is registered as a bean definition rather than as a manually
 * added BeanPostProcessor. As a {@code MergedBeanDefinitionPostProcessor}, it
 * is re-registered at the end of Spring's BeanPostProcessor chain, after
 * Boot's configuration-property binding and Hikari connection-details
 * post-processors. The guard also installs a manually registered sentinel that
 * cannot be removed through the bean-definition registry; it rechecks the
 * verifier definition and final datasource state before each bean
 * initialization. Custom merged-definition processors are rejected because
 * Spring's stable ordering cannot prove a test-defined processor will not run
 * after the verifier.</p>
 */
public final class TestDatasourceGuardApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    static final int ORDER = Ordered.LOWEST_PRECEDENCE;
    static final String VERIFIER_BEAN_NAME = "testDatasourceRuntimeVerifier";

    private final TestDatasourceRuntimeVerifier verifier;

    public TestDatasourceGuardApplicationContextInitializer() {
        this(new TestDatasourceRuntimeVerifier());
    }

    TestDatasourceGuardApplicationContextInitializer(TestDatasourceRuntimeVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        if (environment.getPropertySources()
                .contains(TestDatasourceGuardEnvironmentPostProcessor.TEST_PROFILE_CONFIG_SOURCE_NAME)) {
            // Initializers are applied after all ContextCustomizers (including
            // @DynamicPropertySource), so this catches property sources added
            // after the environment post-processor already ran.
            new TestDatasourceGuardEnvironmentPostProcessor().validateEnvironment(environment);
        }

        applicationContext.addBeanFactoryPostProcessor(
                new TestDatasourceBeanDefinitionGuard(verifier));

        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        if (beanFactory instanceof BeanDefinitionRegistry registry
                && !registry.containsBeanDefinition(VERIFIER_BEAN_NAME)) {
            RootBeanDefinition definition =
                    new RootBeanDefinition(TestDatasourceRuntimeVerifier.class);
            definition.setInstanceSupplier(() -> verifier);
            definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            definition.setPrimary(false);
            registry.registerBeanDefinition(VERIFIER_BEAN_NAME, definition);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
