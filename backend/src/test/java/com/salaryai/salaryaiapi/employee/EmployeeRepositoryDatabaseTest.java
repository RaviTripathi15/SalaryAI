package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.organization.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL tests for the Employee entity and its organization-scoped
 * repository. The test profile is explicit and the schema is create-drop only
 * against salaryai_test.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeRepositoryDatabaseTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void persistsEmployeeLinkedToOrganization() {
        Organization organization = persistOrganization("persist");

        Employee saved = employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-PERSIST", "ada.persist@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrganization().getId()).isEqualTo(organization.getId());

        entityManager.clear();
        Employee reloaded = entityManager.find(Employee.class, saved.getId());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getOrganization().getId()).isEqualTo(organization.getId());
        assertThat(reloaded.getEmployeeCode()).isEqualTo("EMP-PERSIST");
    }

    @Test
    void retrievesEmployeesUsingOrganizationScopedRepositoryMethods() {
        Organization firstOrg = persistOrganization("scoped-first");
        Organization secondOrg = persistOrganization("scoped-second");

        Employee firstEmployee = employeeRepository.saveAndFlush(
                newEmployee(firstOrg, "EMP-S1", "scoped.first@example.com"));
        Employee secondEmployee = employeeRepository.saveAndFlush(
                newEmployee(firstOrg, "EMP-S2", "scoped.second@example.com"));
        Employee otherOrgEmployee = employeeRepository.saveAndFlush(
                newEmployee(secondOrg, "EMP-S3", "scoped.other@example.com"));

        assertThat(employeeRepository.findByOrganizationIdAndId(
                firstOrg.getId(), firstEmployee.getId()))
                .map(Employee::getId)
                .contains(firstEmployee.getId());
        assertThat(employeeRepository.findByOrganizationIdAndId(
                secondOrg.getId(), firstEmployee.getId()))
                .isEmpty();

        List<Employee> firstOrgEmployees =
                employeeRepository.findAllByOrganizationId(firstOrg.getId());
        assertThat(firstOrgEmployees)
                .extracting(Employee::getId)
                .containsExactlyInAnyOrder(
                        firstEmployee.getId(), secondEmployee.getId());
        assertThat(employeeRepository.findAllByOrganizationId(secondOrg.getId()))
                .extracting(Employee::getId)
                .containsExactly(otherOrgEmployee.getId());
    }

    @Test
    void allowsSameEmployeeCodeAndEmailAcrossDifferentOrganizations() {
        Organization firstOrg = persistOrganization("shared-first");
        Organization secondOrg = persistOrganization("shared-second");

        Employee first = employeeRepository.saveAndFlush(
                newEmployee(firstOrg, "EMP-SHARED", "shared.employee@example.com"));
        Employee second = employeeRepository.saveAndFlush(
                newEmployee(secondOrg, "EMP-SHARED", "shared.employee@example.com"));

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(employeeRepository.findAllByOrganizationId(firstOrg.getId())).hasSize(1);
        assertThat(employeeRepository.findAllByOrganizationId(secondOrg.getId())).hasSize(1);
    }

    @Test
    void rejectsDuplicateEmployeeCodeWithinOrganization() {
        Organization organization = persistOrganization("dup-code");
        employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-DUP", "first.dup@example.com"));

        Employee duplicate = newEmployee(
                organization, "EMP-DUP", "second.dup@example.com");

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateWorkEmailWithinOrganization() {
        Organization organization = persistOrganization("dup-email");
        employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-FIRST", "duplicate@example.com"));

        Employee duplicate = newEmployee(
                organization, "EMP-SECOND", "duplicate@example.com");

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingEmployeeCode() {
        Organization organization = persistOrganization("missing-code");
        Employee employee = newEmployee(organization, "EMP-CODE", "code@example.com");
        setField(employee, "employeeCode", null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingFirstName() {
        Organization organization = persistOrganization("missing-first");
        Employee employee = newEmployee(organization, "EMP-FIRST-NAME", "first@example.com");
        employee.setFirstName(null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingLastName() {
        Organization organization = persistOrganization("missing-last");
        Employee employee = newEmployee(organization, "EMP-LAST-NAME", "last@example.com");
        employee.setLastName(null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingWorkEmail() {
        Organization organization = persistOrganization("missing-email");
        Employee employee = newEmployee(organization, "EMP-EMAIL", "email@example.com");
        employee.setWorkEmail(null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingJoiningDate() {
        Organization organization = persistOrganization("missing-joining");
        Employee employee = newEmployee(organization, "EMP-JOIN", "joining@example.com");
        setField(employee, "joiningDate", null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingEmploymentStatus() {
        Organization organization = persistOrganization("missing-status");
        Employee employee = newEmployee(organization, "EMP-STATUS", "status@example.com");
        setField(employee, "employmentStatus", null);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsEmployeeWithInvalidOrganizationForeignKey() {
        Organization missingOrganization = entityManager.getEntityManager()
                .getReference(Organization.class, Long.MAX_VALUE);
        Employee employee = newEmployee(
                missingOrganization, "EMP-FK", "foreign.key@example.com");

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(employee))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void defaultsEmploymentStatusToActive() {
        Organization organization = persistOrganization("default-status");

        Employee saved = employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-ACTIVE", "active@example.com"));

        assertThat(saved.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);

        entityManager.clear();
        assertThat(entityManager.find(Employee.class, saved.getId()).getEmploymentStatus())
                .isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void deactivateSetsInactiveStatusAndDeactivatedAt() {
        Organization organization = persistOrganization("deactivate");
        Employee saved = employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-INACTIVE", "inactive@example.com"));

        assertThat(saved.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(saved.getDeactivatedAt()).isNull();

        saved.deactivate();
        employeeRepository.saveAndFlush(saved);

        entityManager.clear();
        Employee reloaded = entityManager.find(Employee.class, saved.getId());

        assertThat(reloaded.getEmploymentStatus()).isEqualTo(EmploymentStatus.INACTIVE);
        assertThat(reloaded.getDeactivatedAt()).isNotNull();
    }

    @Test
    void setsCreationAndUpdateTimestamps() throws InterruptedException {
        Organization organization = persistOrganization("timestamps");
        Employee saved = employeeRepository.saveAndFlush(
                newEmployee(organization, "EMP-TIME", "timestamps@example.com"));

        LocalDateTime createdAt = saved.getCreatedAt();
        LocalDateTime updatedAt = saved.getUpdatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(updatedAt).isAfterOrEqualTo(createdAt);

        Thread.sleep(15);
        saved.setDepartment("Engineering");
        employeeRepository.saveAndFlush(saved);

        assertThat(saved.getUpdatedAt()).isAfter(updatedAt);

        entityManager.clear();
        Employee reloaded = entityManager.find(Employee.class, saved.getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void repositoryDerivedQueriesStartSuccessfully() {
        assertThat(employeeRepository).isNotNull();
        assertThat(employeeRepository.findByOrganizationIdAndId(0L, 0L)).isEmpty();
        assertThat(employeeRepository.findAllByOrganizationId(0L)).isEmpty();
        assertThat(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                0L, "missing")).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndWorkEmail(
                0L, "missing@example.com")).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                0L, "missing@example.com", 0L)).isFalse();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Could not set field for constraint test: " + fieldName, exception);
        }
    }

    private Organization persistOrganization(String suffix) {
        Organization organization = new Organization(
                "Organization " + suffix,
                suffix + ".org@example.com",
                "5550100",
                "test-hash");
        return entityManager.persistAndFlush(organization);
    }

    private Employee newEmployee(
            Organization organization, String employeeCode, String workEmail) {

        return new Employee(
                organization,
                employeeCode,
                "Ada",
                "Lovelace",
                workEmail,
                LocalDate.of(2024, 1, 15));
    }
}
