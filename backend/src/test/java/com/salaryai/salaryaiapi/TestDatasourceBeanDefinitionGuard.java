package com.salaryai.salaryaiapi;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test-only {@link BeanFactoryPostProcessor} that inspects datasource-related
 * bean definitions after configuration-class processing but before any
 * datasource, Hikari pool, Hibernate, JDBC or schema initialization.
 *
 * <p>The guard never calls {@code getBean()}, never instantiates a bean and
 * never reads a property value or password. It uses bean names, aliases,
 * factory-bean and factory-method metadata, declared return types and relevant
 * annotations. Declared metadata is never treated as proof of an actual runtime
 * type: a suspicious definition whose declared type is unrelated is still
 * rejected. Signatures that provide no safe datasource hint (for example an
 * innocuously named method declared as {@code Object}) cannot be definitively
 * classified at this phase; the final runtime verifier separately checks the
 * effective datasource candidates after all post-processors have run.</p>
 *
 * <p>Spring's stable post-processor sorting cannot prove that a test-defined
 * {@link MergedBeanDefinitionPostProcessor} will run before the verifier, so
 * only exact framework implementations are allowed in that chain. A manually
 * registered sentinel rechecks the verifier definition and final datasource
 * state if a later bean-defined BeanFactoryPostProcessor removes or replaces
 * the verifier definition.</p>
 */
public final class TestDatasourceBeanDefinitionGuard implements BeanFactoryPostProcessor {

    private static final String VERIFIER_BEAN_NAME = "testDatasourceRuntimeVerifier";
    private static final String VERIFIER_CLASS_NAME =
            "com.salaryai.salaryaiapi.TestDatasourceRuntimeVerifier";
    private static final String PRIMARY_ANNOTATION = "org.springframework.context.annotation.Primary";
    private static final String BEAN_ANNOTATION = "org.springframework.context.annotation.Bean";

    private static final Set<String> RESERVED_BEAN_NAMES = Set.of(
            TestDatasourceConstants.DATASOURCE_BEAN_NAME,
            TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME,
            TestDatasourceConstants.DATASOURCE_PROPERTIES_BEAN_NAME,
            TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_BEAN_NAME,
            TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_BEAN_NAME);

    private static final Set<String> RESERVED_FACTORY_METHODS = Set.of(
            TestDatasourceConstants.DATASOURCE_BEAN_NAME,
            TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME,
            TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_BEAN_NAME,
            TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_BEAN_NAME);

    /**
     * Framework beans that contain a datasource word in their class name but are
     * consumers, not datasource definitions. They must not be rejected merely
     * because of the conservative name-based heuristic.
     */
    private static final Set<String> KNOWN_CONSUMER_CLASS_NAMES = Set.of(
            "org.springframework.jdbc.datasource.DataSourceTransactionManager",
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDependsOnDatabaseInitializationDetector",
            "org.springframework.boot.sql.init.dependency.AnnotationDependsOnDatabaseInitializationDetector");

    /**
     * Boot configuration classes that are approved datasource infrastructure but
     * contain datasource words in their class names. They are configuration
     * metadata, not custom datasource definitions.
     */
    private static final Set<String> KNOWN_APPROVED_INFRASTRUCTURE_CLASS_NAMES = Set.of(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration$PooledDataSourceConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceConfiguration$Hikari",
            "org.springframework.boot.jdbc.autoconfigure.DataSourcePoolMetadataProvidersConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourcePoolMetadataProvidersConfiguration$HikariPoolDataSourceMetadataProviderConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JndiDataSourceAutoConfiguration");

    /**
     * Exact framework {@link MergedBeanDefinitionPostProcessor} definitions that
     * Spring registers itself. Only these exact bean-name/class pairs may remain
     * in the post-processor chain alongside the verifier: any other merged
     * definition processor could be re-registered after it and mutate the final
     * datasource. Programmatic test registrations that merely declare a Spring
     * interface as their type are never trusted.
     */
    private static final Map<String, String> FRAMEWORK_MERGED_POST_PROCESSORS = Map.of(
            "org.springframework.context.annotation.internalAutowiredAnnotationProcessor",
                    "org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor",
            "org.springframework.context.annotation.internalCommonAnnotationProcessor",
                    "org.springframework.context.annotation.CommonAnnotationBeanPostProcessor",
            "org.springframework.context.annotation.internalPersistenceAnnotationProcessor",
                    "org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor");

    private static final Set<String> FRAMEWORK_MERGED_POST_PROCESSOR_CLASSES = Set.of(
            "org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor",
            "org.springframework.context.annotation.CommonAnnotationBeanPostProcessor",
            "org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor",
            "org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor");

    /**
     * Classes allowed to remain in the live BeanPostProcessor chain. The
     * framework detector is added manually by Spring itself and is not present
     * as a bean definition.
     */
    static final Set<String> ALLOWED_MERGED_POST_PROCESSOR_CLASSES = Set.of(
            VERIFIER_CLASS_NAME,
            "org.springframework.context.support.ApplicationListenerDetector",
            "org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor",
            "org.springframework.context.annotation.CommonAnnotationBeanPostProcessor",
            "org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor",
            "org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor");


    private enum Kind {
        APPROVED_DATA_SOURCE,
        APPROVED_CONNECTION_DETAILS,
        APPROVED_PROPERTIES,
        APPROVED_INFRASTRUCTURE,
        REJECTED
    }

    private record Decision(Kind kind, String problem) {
    }

    private final TestDatasourceRuntimeVerifier verifier;

    public TestDatasourceBeanDefinitionGuard(TestDatasourceRuntimeVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Set<String> problems = new TreeSet<>();
        Set<String> postProcessorProblems = new TreeSet<>();
        Set<String> inScopeNames = new TreeSet<>();
        int approvedDataSources = 0;
        int approvedConnectionDetails = 0;
        int approvedProperties = 0;
        int verifierDefinitions = 0;

        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw new IllegalStateException(
                    "Unsafe test datasource bean definitions: bean factory is not a BeanDefinitionRegistry");
        }

        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (isVerifierDefinition(name, definition)) {
                verifierDefinitions++;
                if (!VERIFIER_BEAN_NAME.equals(name)
                        || !VERIFIER_CLASS_NAME.equals(resolvedClassName(definition))) {
                    problems.add("test datasource verifier definition is missing, replaced, "
                            + "or duplicated");
                }
                continue;
            }

            String[] aliases = registry.getAliases(name);
            boolean reserved = isReserved(name, definition, aliases);
            Class<?> type = resolveType(
                    definition, beanFactory, registry, name, reserved || isSuspiciousName(name), problems);
            boolean suspicious = isSuspiciousDefinition(
                    name, definition, aliases, type, reserved);
            boolean datasourceType = isDataSourceRelatedType(type);

            // A custom MergedBeanDefinitionPostProcessor can be re-registered after
            // the verifier in Spring's internal post-processor pass and mutate the
            // final datasource. Reject it whenever datasource infrastructure exists.
            if (isMergedPostProcessorDefinition(beanFactory, name, definition)
                    && !isAllowedMergedPostProcessor(name, definition)) {
                postProcessorProblems.add(
                        "custom MergedBeanDefinitionPostProcessor definition '" + name
                                + "' is not permitted");
            }

            if (!reserved && !suspicious && !datasourceType) {
                continue;
            }
            inScopeNames.add(name);

            Decision decision = classify(name, definition, aliases, type, registry, suspicious);
            switch (decision.kind()) {
                case APPROVED_DATA_SOURCE -> approvedDataSources++;
                case APPROVED_CONNECTION_DETAILS -> approvedConnectionDetails++;
                case APPROVED_PROPERTIES -> approvedProperties++;
                case APPROVED_INFRASTRUCTURE -> {
                    // Approved Boot-managed Hikari infrastructure; no count requirement.
                }
                case REJECTED -> problems.add(decision.problem());
            }
        }

        // A datasource-free test slice has no count requirements, but the
        // sentinel is still installed: a later bean-defined BeanFactoryPostProcessor
        // may add a datasource definition, and that definition must be rejected
        // before its bean is instantiated.
        if (inScopeNames.isEmpty()) {
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Unsafe test datasource bean definitions: " + String.join("; ", problems));
            }
            installSentinel(beanFactory);
            return;
        }

        if (verifierDefinitions != 1) {
            problems.add("expected exactly one test datasource verifier definition");
        }
        if (approvedDataSources != 1) {
            problems.add("expected exactly one verified Boot-managed Hikari 'dataSource' definition");
        }
        if (approvedConnectionDetails != 1) {
            problems.add("expected exactly one verified Boot-managed 'jdbcConnectionDetails' definition");
        }
        if (approvedProperties != 1) {
            problems.add("expected exactly one verified Boot-managed DataSourceProperties definition");
        }
        problems.addAll(postProcessorProblems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe test datasource bean definitions: " + String.join("; ", problems)
                            + ". Tests may only use verified Boot-managed Hikari infrastructure "
                            + "for the isolated salaryai_test database.");
        }

        installSentinel(beanFactory);
    }

    private void installSentinel(ConfigurableListableBeanFactory beanFactory) {
        // A bean-defined BFPP can still remove or replace the verifier definition after
        // this scan. A manually registered sentinel cannot be removed through the
        // bean definition registry and re-checks the verifier, datasource definitions
        // and final datasource state before every bean initialization.
        beanFactory.addBeanPostProcessor(
                new TestDatasourceLifecycleSentinel(verifier, beanFactory));
    }

    /**
     * Repeats the bean-definition classification immediately before a bean is
     * initialized. This closes the gap where a datasource-free context gains a
     * custom datasource definition through a later BeanFactoryPostProcessor.
     */
    static void verifyCurrentDefinitions(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }
        Set<String> problems = new TreeSet<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (isVerifierDefinition(name, definition)) {
                continue;
            }
            String[] aliases = registry.getAliases(name);
            boolean reserved = isReserved(name, definition, aliases);
            Class<?> type = resolveType(
                    definition, beanFactory, registry, name,
                    reserved || isSuspiciousName(name), problems);
            boolean suspicious = isSuspiciousDefinition(
                    name, definition, aliases, type, reserved);
            boolean datasourceType = isDataSourceRelatedType(type);

            if (isMergedPostProcessorDefinition(beanFactory, name, definition)
                    && !isAllowedMergedPostProcessor(name, definition)) {
                problems.add("custom MergedBeanDefinitionPostProcessor definition '" + name
                        + "' is not permitted");
            }
            if (!reserved && !suspicious && !datasourceType) {
                continue;
            }
            Decision decision = classify(name, definition, aliases, type, registry, suspicious);
            if (decision.kind() == Kind.REJECTED) {
                problems.add(decision.problem());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Unsafe test datasource lifecycle definitions: " + String.join("; ", problems));
        }
    }

    private static Decision classify(
            String name, BeanDefinition definition, String[] aliases, Class<?> type,
            BeanDefinitionRegistry registry, boolean suspicious) {

        if (type == null) {
            return rejected("unclassifiable datasource-related definition '" + name + "'");
        }

        String typeName = type.getName();

        if (XADataSource.class.isAssignableFrom(type)) {
            return rejected("XADataSource definition '" + name + "' is not permitted");
        }
        if (TestDatasourceConstants.HIKARI_DATASOURCE_CLASS_NAME.equals(typeName)) {
            if (isApprovedFactory(name, definition, type, registry,
                    TestDatasourceConstants.DATASOURCE_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_CONFIGURATION_CLASS_NAME,
                    TestDatasourceConstants.DATASOURCE_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_DATASOURCE_CLASS_NAME)
                    && !hasSuspiciousAlias(aliases)) {
                return new Decision(Kind.APPROVED_DATA_SOURCE, null);
            }
            return rejected("custom HikariDataSource definition '" + name + "' is not permitted");
        }
        if (DataSource.class.isAssignableFrom(type)) {
            return rejected("custom DataSource definition '" + name + "' is not permitted");
        }
        if (JdbcConnectionDetails.class.isAssignableFrom(type)) {
            if (isApprovedFactory(name, definition, type, registry,
                    TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME,
                    TestDatasourceConstants.POOLED_CONFIGURATION_CLASS_NAME,
                    TestDatasourceConstants.JDBC_CONNECTION_DETAILS_BEAN_NAME,
                    TestDatasourceConstants.PROPERTIES_CONNECTION_DETAILS_CLASS_NAME)
                    && !hasSuspiciousAlias(aliases)) {
                return new Decision(Kind.APPROVED_CONNECTION_DETAILS, null);
            }
            return rejected("custom JdbcConnectionDetails definition '" + name + "' is not permitted");
        }
        if (DataSourceProperties.class.isAssignableFrom(type)) {
            if (TestDatasourceConstants.DATASOURCE_PROPERTIES_BEAN_NAME.equals(name)
                    && TestDatasourceConstants.DATASOURCE_PROPERTIES_CLASS_NAME.equals(typeName)
                    && definition.getFactoryBeanName() == null
                    && definition.getFactoryMethodName() == null
                    && !hasSuspiciousAlias(aliases)) {
                return new Decision(Kind.APPROVED_PROPERTIES, null);
            }
            return rejected("custom DataSourceProperties definition '" + name + "' is not permitted");
        }
        if (TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_CLASS_NAME.equals(typeName)) {
            if (isApprovedFactory(name, definition, type, registry,
                    TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_CONFIGURATION_CLASS_NAME,
                    TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_CLASS_NAME)
                    && !hasSuspiciousAlias(aliases)) {
                return new Decision(Kind.APPROVED_INFRASTRUCTURE, null);
            }
            return rejected("custom Hikari connection-details processor definition '" + name
                    + "' is not permitted");
        }
        if (TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_CLASS_NAME.equals(typeName)) {
            if (isApprovedFactory(name, definition, type, registry,
                    TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_POOL_METADATA_CONFIGURATION_CLASS_NAME,
                    TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_BEAN_NAME,
                    TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_CLASS_NAME)
                    && !hasSuspiciousAlias(aliases)) {
                return new Decision(Kind.APPROVED_INFRASTRUCTURE, null);
            }
            return rejected("custom datasource metadata provider definition '" + name
                    + "' is not permitted");
        }

        if (suspicious) {
            return rejected("suspicious datasource-related definition '" + name
                    + "' with declared type '" + typeName
                    + "' is not permitted (declared metadata does not prove the runtime type)");
        }
        return rejected("unclassifiable datasource-related definition '" + name + "'");
    }

    private static boolean isApprovedFactory(
            String name, BeanDefinition definition, Class<?> type, BeanDefinitionRegistry registry,
            String expectedBeanName, String expectedFactoryClass, String expectedMethod,
            String expectedReturnType) {

        if (!expectedBeanName.equals(name)
                || !expectedMethod.equals(definition.getFactoryMethodName())
                || !expectedReturnType.equals(type.getName())) {
            return false;
        }
        if (expectedFactoryClass.equals(definition.getFactoryBeanName())) {
            try {
                BeanDefinition factoryDefinition = registry.getBeanDefinition(expectedFactoryClass);
                return expectedFactoryClass.equals(factoryDefinition.getBeanClassName());
            }
            catch (RuntimeException ex) {
                return false;
            }
        }
        // Static @Bean methods are registered without a factory bean name; the
        // declaring configuration class is recorded in the factory-method metadata.
        return definition.getFactoryBeanName() == null
                && expectedFactoryClass.equals(declaringClassName(definition, registry));
    }

    private static boolean isReserved(String name, BeanDefinition definition, String[] aliases) {
        if (RESERVED_BEAN_NAMES.contains(name) || hasReservedAlias(aliases)) {
            return true;
        }
        String factoryMethod = definition.getFactoryMethodName();
        return factoryMethod != null && RESERVED_FACTORY_METHODS.contains(factoryMethod);
    }

    private static boolean isVerifierDefinition(String name, BeanDefinition definition) {
        return VERIFIER_BEAN_NAME.equals(name)
                || VERIFIER_CLASS_NAME.equals(resolvedClassName(definition));
    }

    private static String resolvedClassName(BeanDefinition definition) {
        if (definition.getBeanClassName() != null) {
            return definition.getBeanClassName();
        }
        Class<?> resolved = definition.getResolvableType().resolve();
        return resolved == null ? null : resolved.getName();
    }

    private static boolean isSuspiciousDefinition(
            String name, BeanDefinition definition, String[] aliases, Class<?> type,
            boolean reserved) {

        if (hasSuspiciousAlias(aliases)) {
            return true;
        }
        if (reserved) {
            // Reserved names are in scope even when their declared type is unrelated;
            // classification decides whether they are approved or rejected.
            return true;
        }
        if (isSuspiciousName(name) || isSuspiciousName(definition.getFactoryMethodName())) {
            return true;
        }
        String factoryBeanName = definition.getFactoryBeanName();
        if (isSuspiciousName(factoryBeanName)) {
            return true;
        }
        String factoryBeanClassName = factoryBeanName == null
                ? null
                : safeFactoryBeanClassName(factoryBeanName, definition);
        if (isSuspiciousTypeName(factoryBeanClassName)) {
            return true;
        }
        String declaredTypeName = type == null ? definition.getBeanClassName() : type.getName();
        if (declaredTypeName != null
                && !KNOWN_CONSUMER_CLASS_NAMES.contains(declaredTypeName)
                && isSuspiciousTypeName(declaredTypeName)) {
            return true;
        }
        if (definition instanceof AnnotatedBeanDefinition annotated) {
            MethodMetadata methodMetadata = annotated.getFactoryMethodMetadata();
            if (methodMetadata != null) {
                if (isSuspiciousName(methodMetadata.getMethodName())) {
                    return true;
                }
                if (isSuspiciousTypeName(methodMetadata.getReturnTypeName())) {
                    return true;
                }
                if (methodMetadata.isAnnotated(PRIMARY_ANNOTATION)
                        && (isSuspiciousName(name)
                        || isSuspiciousTypeName(declaredTypeName)
                        || isDataSourceRelatedType(type))) {
                    return true;
                }
            }
            if (annotated.getMetadata().hasAnnotation(PRIMARY_ANNOTATION)
                    && (isSuspiciousName(name)
                    || isSuspiciousTypeName(declaredTypeName)
                    || isDataSourceRelatedType(type))) {
                return true;
            }
        }
        return false;
    }

    private static String safeFactoryBeanClassName(
            String factoryBeanName, BeanDefinition definition) {

        if (definition instanceof AnnotatedBeanDefinition annotated) {
            MethodMetadata metadata = annotated.getFactoryMethodMetadata();
            if (metadata != null && metadata.getDeclaringClassName() != null) {
                return metadata.getDeclaringClassName();
            }
        }
        return null;
    }

    private static boolean hasReservedAlias(String[] aliases) {
        for (String alias : aliases) {
            if (RESERVED_BEAN_NAMES.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSuspiciousAlias(String[] aliases) {
        for (String alias : aliases) {
            if (isSuspiciousName(alias)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDataSourceRelatedType(Class<?> type) {
        if (type == null) {
            return false;
        }
        return XADataSource.class.isAssignableFrom(type)
                || DataSource.class.isAssignableFrom(type)
                || JdbcConnectionDetails.class.isAssignableFrom(type)
                || DataSourceProperties.class.isAssignableFrom(type)
                || TestDatasourceConstants.HIKARI_CONNECTION_DETAILS_PROCESSOR_CLASS_NAME.equals(type.getName())
                || TestDatasourceConstants.HIKARI_POOL_METADATA_PROVIDER_CLASS_NAME.equals(type.getName());
    }

    private static boolean isSuspiciousName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace('$', '.');
        return normalized.endsWith("datasource")
                || normalized.endsWith("xadatasource")
                || normalized.endsWith("connectiondetails")
                || normalized.endsWith("hikaridatasource")
                || normalized.endsWith("jndidatasource")
                || normalized.endsWith("basicdatasource")
                || normalized.endsWith("pooldatasource")
                || normalized.endsWith("datasourceproperties");
    }

    private static boolean isSuspiciousTypeName(String className) {
        if (className == null || className.isBlank()
                || KNOWN_CONSUMER_CLASS_NAMES.contains(className)
                || KNOWN_APPROVED_INFRASTRUCTURE_CLASS_NAMES.contains(className)) {
            return false;
        }
        String normalized = className.toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace('$', '.');
        return normalized.endsWith("datasource")
                || normalized.endsWith("xadatasource")
                || normalized.contains("hikaridatasource")
                || normalized.contains("jndidatasource")
                || normalized.contains("pooldatasource")
                || normalized.contains("basicdatasource")
                || normalized.contains("jdbcconnectiondetails")
                || normalized.contains("datasourceproperties");
    }

    private static Class<?> resolveType(
            BeanDefinition definition, ConfigurableListableBeanFactory beanFactory,
            BeanDefinitionRegistry registry, String name, boolean failClosed, Set<String> problems) {

        try {
            // For @Bean definitions, the resolvable type can describe the
            // declaring configuration class instead of the bean's return type.
            // Prefer the factory method's declared return type; this is read
            // reflectively and does not instantiate the factory or the bean.
            Class<?> factoryMethodType = resolveFactoryMethodType(
                    definition, registry, beanFactory.getBeanClassLoader());
            if (factoryMethodType != null) {
                return factoryMethodType;
            }
            Class<?> resolved = definition.getResolvableType().resolve();
            if (resolved != null) {
                return resolved;
            }
            String className = definition.getBeanClassName();
            if (className == null) {
                return null;
            }
            return ClassUtils.forName(className, beanFactory.getBeanClassLoader());
        }
        catch (Exception | LinkageError ex) {
            if (failClosed || isSuspiciousTypeName(definition.getBeanClassName())) {
                problems.add("unresolvable datasource-related definition '" + name + "'");
            }
            return null;
        }
    }

    private static Class<?> resolveFactoryMethodType(
            BeanDefinition definition, BeanDefinitionRegistry registry, ClassLoader classLoader)
            throws ClassNotFoundException {

        String methodName = definition.getFactoryMethodName();
        if (methodName == null) {
            return null;
        }
        String declaringClassName = declaringClassName(definition, registry);
        if (declaringClassName == null) {
            return null;
        }
        Class<?> declaringClass = ClassUtils.forName(declaringClassName, classLoader);
        Class<?> fallback = null;
        for (Method method : declaringClass.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getReturnType() == void.class) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (isDataSourceRelatedType(returnType)) {
                return returnType;
            }
            if (fallback == null) {
                fallback = returnType;
            }
        }
        return fallback;
    }

    private static String declaringClassName(
            BeanDefinition definition, BeanDefinitionRegistry registry) {

        if (definition instanceof AnnotatedBeanDefinition annotated) {
            MethodMetadata metadata = annotated.getFactoryMethodMetadata();
            if (metadata != null && metadata.getDeclaringClassName() != null) {
                return metadata.getDeclaringClassName();
            }
        }
        if (definition.getFactoryBeanName() != null) {
            try {
                return registry.getBeanDefinition(definition.getFactoryBeanName()).getBeanClassName();
            }
            catch (RuntimeException ex) {
                return null;
            }
        }
        return definition.getBeanClassName();
    }

    static boolean isAllowedMergedPostProcessor(String name, BeanDefinition definition) {
        String className = resolvedClassName(definition);
        if (className == null) {
            return false;
        }
        // Exact internal bean-name/class pairs registered by Spring itself.
        if (className.equals(FRAMEWORK_MERGED_POST_PROCESSORS.get(name))) {
            return true;
        }
        // A framework @Bean method may also declare one of the exact trusted
        // implementation classes. A subclass or a programmatic registration that
        // merely declares an interface type is never trusted.
        if (!FRAMEWORK_MERGED_POST_PROCESSOR_CLASSES.contains(className)
                || !(definition instanceof AnnotatedBeanDefinition annotated)) {
            return false;
        }
        MethodMetadata metadata = annotated.getFactoryMethodMetadata();
        String declaringClassName = metadata == null ? null : metadata.getDeclaringClassName();
        return declaringClassName != null
                && declaringClassName.startsWith("org.springframework.");
    }

    static boolean isMergedPostProcessorDefinition(
            ConfigurableListableBeanFactory beanFactory, String name, BeanDefinition definition) {

        Class<?> type = definition.getResolvableType().resolve();
        if (type == null && definition.getBeanClassName() != null) {
            try {
                type = ClassUtils.forName(
                        definition.getBeanClassName(), beanFactory.getBeanClassLoader());
            }
            catch (Exception | LinkageError ignored) {
                // Type prediction below is the remaining non-instantiating option.
            }
        }
        if (type != null) {
            return MergedBeanDefinitionPostProcessor.class.isAssignableFrom(type);
        }
        try {
            return beanFactory.isTypeMatch(name, MergedBeanDefinitionPostProcessor.class);
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Used by the runtime verifier to distinguish datasource-bearing contexts
     * from datasource-free test slices. It never instantiates a bean or reads a
     * property value.
     */
    static boolean hasDatasourceScope(ConfigurableListableBeanFactory beanFactory) {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return true;
        }
        Set<String> ignoredProblems = new TreeSet<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition = beanFactory.getBeanDefinition(name);
            if (isVerifierDefinition(name, definition)) {
                continue;
            }
            String[] aliases = registry.getAliases(name);
            if (isReserved(name, definition, aliases)
                    || isSuspiciousDefinition(name, definition, aliases, null, false)) {
                return true;
            }
            Class<?> type = resolveType(
                    definition, beanFactory, registry, name, false, ignoredProblems);
            if (isDataSourceRelatedType(type)) {
                return true;
            }
        }
        return false;
    }

    private static Decision rejected(String problem) {
        return new Decision(Kind.REJECTED, problem);
    }

    /**
     * Manually registered monitor that fails closed if a later bean-defined
     * BeanFactoryPostProcessor removes or replaces the verifier definition. It
     * also repeats the final datasource verification before each bean is
     * initialized, so any post-verification mutation is caught before a
     * datasource consumer can connect.
     */
    private static final class TestDatasourceLifecycleSentinel implements BeanPostProcessor {

        private final TestDatasourceRuntimeVerifier verifier;
        private final ConfigurableListableBeanFactory beanFactory;

        private TestDatasourceLifecycleSentinel(
                TestDatasourceRuntimeVerifier verifier,
                ConfigurableListableBeanFactory beanFactory) {
            this.verifier = verifier;
            this.beanFactory = beanFactory;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (!beanFactory.containsBeanDefinition(VERIFIER_BEAN_NAME)) {
                throw new IllegalStateException(
                        "Unsafe test datasource lifecycle: the test datasource verifier "
                                + "definition was removed");
            }
            BeanDefinition definition = beanFactory.getBeanDefinition(VERIFIER_BEAN_NAME);
            if (!VERIFIER_CLASS_NAME.equals(resolvedClassName(definition))) {
                throw new IllegalStateException(
                        "Unsafe test datasource lifecycle: the test datasource verifier "
                                + "definition was replaced");
            }
            verifyCurrentDefinitions(beanFactory);
            verifier.verifyFinalState(beanName);
            return bean;
        }
    }
}
