package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.admin.PlatformAdmin;
import com.salaryai.salaryaiapi.employee.Employee;
import com.salaryai.salaryaiapi.organization.Organization;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway chain and Hibernate entity mapping inside a dedicated
 * schema in the isolated salaryai_test database. Flyway runs programmatically
 * through the existing DataSource bean, so the strict datasource guards remain
 * untouched and the development database is never contacted.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttendanceMigrationTest {

    private static final String SCHEMA =
            "attendance_mig_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private DataSource dataSource;

    @AfterAll
    void dropDedicatedSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertIsolatedTestDatabase(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
            }
        }
    }

    @Test
    void migrationsApplyInDedicatedSchemaAndHibernateMappingIsValid() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);

        try (Connection connection = dataSource.getConnection()) {
            assertIsolatedTestDatabase(connection);
            assertMigratedSchema(connection);
        }

        validateHibernateMapping();
    }

    private void assertMigratedSchema(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            statement.setString(1, SCHEMA);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
        }

        assertThat(tables).contains(
                "organizations",
                "platform_admins",
                "employees",
                "attendance_settings",
                "attendance_schedules",
                "employee_schedule_assignments",
                "attendance_events",
                "daily_attendance",
                "leave_types",
                "leave_requests",
                "holidays",
                "attendance_corrections",
                "attendance_audit_records",
                "flyway_schema_history");
        assertThat(tables).noneMatch(table -> table.toLowerCase().contains("month"));

        List<String> versions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, success FROM \"" + SCHEMA + "\".flyway_schema_history "
                        + "ORDER BY installed_rank")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    assertThat(rows.getBoolean("success")).isTrue();
                    String version = rows.getString("version");
                    if (version != null) {
                        versions.add(version);
                    }
                }
            }
        }
        assertThat(versions).containsExactly("1", "2", "3");

        Map<String, String> columnTypes = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name, column_name, data_type "
                        + "FROM information_schema.columns WHERE table_schema = ?")) {
            statement.setString(1, SCHEMA);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    columnTypes.put(rows.getString(1) + "." + rows.getString(2),
                            rows.getString(3));
                }
            }
        }
        assertThat(columnTypes).containsEntry(
                "attendance_events.occurred_at", "timestamp with time zone");
        assertThat(columnTypes).containsEntry(
                "daily_attendance.first_check_in", "timestamp with time zone");
        assertThat(columnTypes).containsEntry(
                "attendance_corrections.corrected_at", "timestamp with time zone");
        assertThat(columnTypes).containsEntry(
                "attendance_audit_records.occurred_at", "timestamp with time zone");
    }

    private void validateHibernateMapping() {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DATASOURCE, dataSource);
        settings.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                CamelCaseToUnderscoresNamingStrategy.class.getName());
        settings.put(AvailableSettings.DEFAULT_SCHEMA, SCHEMA);
        settings.put(AvailableSettings.HBM2DDL_AUTO, "validate");
        settings.put(AvailableSettings.JAKARTA_HBM2DDL_DATABASE_ACTION, "validate");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(Organization.class);
            sources.addAnnotatedClass(PlatformAdmin.class);
            sources.addAnnotatedClass(Employee.class);
            sources.addAnnotatedClass(AttendanceSettings.class);
            sources.addAnnotatedClass(AttendanceSchedule.class);
            sources.addAnnotatedClass(EmployeeScheduleAssignment.class);
            sources.addAnnotatedClass(AttendanceEvent.class);
            sources.addAnnotatedClass(DailyAttendance.class);
            sources.addAnnotatedClass(LeaveType.class);
            sources.addAnnotatedClass(LeaveRequest.class);
            sources.addAnnotatedClass(Holiday.class);
            sources.addAnnotatedClass(AttendanceCorrection.class);
            sources.addAnnotatedClass(AttendanceAuditRecord.class);

            Metadata metadata = sources.buildMetadata();
            SchemaManagementToolCoordinator.process(
                    metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertIsolatedTestDatabase(Connection connection) throws SQLException {
        assertThat(connection.getMetaData().getURL()).endsWith("/salaryai_test");
        assertThat(connection.getMetaData().getUserName()).isEqualTo("salaryai_test_user");
    }
}
