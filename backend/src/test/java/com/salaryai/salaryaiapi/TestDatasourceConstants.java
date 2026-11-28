package com.salaryai.salaryaiapi;

/** Shared, non-secret identifiers used by the test datasource guards. */
final class TestDatasourceConstants {

    static final String DATASOURCE_BEAN_NAME = "dataSource";
    static final String JDBC_CONNECTION_DETAILS_BEAN_NAME = "jdbcConnectionDetails";
    static final String DATASOURCE_PROPERTIES_BEAN_NAME =
            "spring.datasource-org.springframework.boot.jdbc.autoconfigure.DataSourceProperties";
    static final String HIKARI_CONNECTION_DETAILS_PROCESSOR_BEAN_NAME =
            "jdbcConnectionDetailsHikariBeanPostProcessor";
    static final String HIKARI_POOL_METADATA_PROVIDER_BEAN_NAME =
            "hikariPoolDataSourceMetadataProvider";

    static final String HIKARI_DATASOURCE_CLASS_NAME = "com.zaxxer.hikari.HikariDataSource";
    static final String HIKARI_CONFIGURATION_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.DataSourceConfiguration$Hikari";
    static final String PROPERTIES_CONNECTION_DETAILS_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.PropertiesJdbcConnectionDetails";
    static final String POOLED_CONFIGURATION_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration$PooledDataSourceConfiguration";
    static final String HIKARI_CONNECTION_DETAILS_PROCESSOR_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.HikariJdbcConnectionDetailsBeanPostProcessor";
    static final String DATASOURCE_PROPERTIES_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.DataSourceProperties";
    static final String HIKARI_POOL_METADATA_PROVIDER_CLASS_NAME =
            "org.springframework.boot.jdbc.metadata.DataSourcePoolMetadataProvider";
    static final String HIKARI_POOL_METADATA_CONFIGURATION_CLASS_NAME =
            "org.springframework.boot.jdbc.autoconfigure.DataSourcePoolMetadataProvidersConfiguration$HikariPoolDataSourceMetadataProviderConfiguration";

    private TestDatasourceConstants() {
    }
}
