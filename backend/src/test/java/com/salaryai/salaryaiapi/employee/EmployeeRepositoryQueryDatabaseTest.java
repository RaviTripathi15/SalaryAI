package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.organization.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL tests for the organization-scoped Employee query methods.
 * Uses only the protected test-profile datasource.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeRepositoryQueryDatabaseTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void filtersAndPaginatesOnlyEmployeesInRequestedOrganization() {
        Organization firstOrg = persistOrganization("filter-first");
        Organization secondOrg = persistOrganization("filter-second");

        Employee ada = employeeRepository.saveAndFlush(employee(
                firstOrg, "EMP-A1", "Ada", "Lovelace",
                "ada.filter@example.com", "Engineering"));
        Employee grace = employeeRepository.saveAndFlush(employee(
                firstOrg, "EMP-A2", "Grace", "Hopper",
                "grace.filter@example.com", "Engineering"));
        Employee inactive = employeeRepository.saveAndFlush(employee(
                firstOrg, "EMP-A3", "Alan", "Turing",
                "alan.filter@example.com", "Research"));
        inactive.deactivate();
        employeeRepository.saveAndFlush(inactive);

        employeeRepository.saveAndFlush(employee(
                secondOrg, "EMP-B1", "Ada", "Other",
                "ada.other@example.com", "Engineering"));

        Page<Employee> page = employeeRepository.findByOrganizationIdWithFilters(
                firstOrg.getId(),
                "%engineering%",
                EmploymentStatus.ACTIVE,
                PageRequest.of(0, 1, Sort.by("lastName").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Employee::getId).containsExactly(grace.getId());

        Page<Employee> secondPage = employeeRepository.findByOrganizationIdWithFilters(
                firstOrg.getId(),
                "%engineering%",
                EmploymentStatus.ACTIVE,
                PageRequest.of(1, 1, Sort.by("lastName").ascending()));

        assertThat(secondPage.getContent()).extracting(Employee::getId).containsExactly(ada.getId());
        assertThat(secondPage.getContent()).doesNotContain(inactive);
    }

    @Test
    void searchEscapesWildcardsAndMatchesLiteralCharacters() {
        Organization organization = persistOrganization("wildcard");

        Employee literal = employeeRepository.saveAndFlush(employee(
                organization, "EMP_100", "Ada", "Lovelace",
                "literal@example.com", "Research"));
        employeeRepository.saveAndFlush(employee(
                organization, "EMPX100", "Grace", "Hopper",
                "wildcard@example.com", "Research"));

        Page<Employee> page = employeeRepository.findByOrganizationIdWithFilters(
                organization.getId(),
                "%\\_%",
                null,
                PageRequest.of(0, 10, Sort.by("employeeCode").ascending()));

        assertThat(page.getContent()).extracting(Employee::getId).containsExactly(literal.getId());
    }

    @Test
    void statusFilterExcludesInactiveEmployees() {
        Organization organization = persistOrganization("status-filter");

        Employee active = employeeRepository.saveAndFlush(employee(
                organization, "EMP-ACTIVE", "Ada", "Lovelace",
                "status.active@example.com", "Research"));
        Employee inactive = employeeRepository.saveAndFlush(employee(
                organization, "EMP-INACTIVE", "Grace", "Hopper",
                "status.inactive@example.com", "Research"));
        inactive.deactivate();
        employeeRepository.saveAndFlush(inactive);

        Page<Employee> activePage = employeeRepository.findByOrganizationIdWithFilters(
                organization.getId(),
                null,
                EmploymentStatus.ACTIVE,
                PageRequest.of(0, 10, Sort.by("employeeCode").ascending()));
        Page<Employee> inactivePage = employeeRepository.findByOrganizationIdWithFilters(
                organization.getId(),
                null,
                EmploymentStatus.INACTIVE,
                PageRequest.of(0, 10, Sort.by("employeeCode").ascending()));

        assertThat(activePage.getContent()).extracting(Employee::getId).containsExactly(active.getId());
        assertThat(inactivePage.getContent()).extracting(Employee::getId).containsExactly(inactive.getId());
    }

    @Test
    void duplicateExclusionChecksRemainScopedToOrganizationAndId() {
        Organization firstOrg = persistOrganization("exclude-first");
        Organization secondOrg = persistOrganization("exclude-second");

        Employee first = employeeRepository.saveAndFlush(employee(
                firstOrg, "EMP-SHARED", "Ada", "Lovelace",
                "exclude.shared@example.com", "Research"));
        Employee second = employeeRepository.saveAndFlush(employee(
                secondOrg, "EMP-SHARED", "Grace", "Hopper",
                "exclude.shared@example.com", "Research"));

        assertThat(employeeRepository.existsByOrganizationIdAndEmployeeCodeAndIdNot(
                firstOrg.getId(), "EMP-SHARED", first.getId())).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndEmployeeCodeAndIdNot(
                firstOrg.getId(), "EMP-SHARED", second.getId())).isTrue();
        assertThat(employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                firstOrg.getId(), "exclude.shared@example.com", second.getId())).isTrue();
        assertThat(employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                secondOrg.getId(), "exclude.shared@example.com", second.getId())).isFalse();
    }

    @Test
    void scopedSaveWrappersRejectForeignOrganization() {
        Organization firstOrg = persistOrganization("save-first");
        Organization secondOrg = persistOrganization("save-second");
        Employee employee = employee(
                secondOrg, "EMP-FOREIGN", "Ada", "Lovelace",
                "foreign.save@example.com", "Research");

        assertThatThrownBy(() ->
                employeeRepository.saveAndFlushScoped(employee, firstOrg.getId()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Employee does not belong to the authenticated organization.");

        Employee saved = employeeRepository.saveAndFlushScoped(employee, secondOrg.getId());
        assertThat(saved.getId()).isNotNull();
    }

    private Organization persistOrganization(String suffix) {
        Organization organization = new Organization(
                "Organization " + suffix,
                suffix + ".org@example.com",
                "5550100",
                "test-hash");
        organization.setStatus("APPROVED");
        return entityManager.persistAndFlush(organization);
    }

    private static Employee employee(
            Organization organization,
            String employeeCode,
            String firstName,
            String lastName,
            String workEmail,
            String department) {

        Employee employee = new Employee(
                organization,
                employeeCode,
                firstName,
                lastName,
                workEmail,
                LocalDate.of(2024, 1, 15));
        employee.setDepartment(department);
        return employee;
    }
}
