package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.employee.dto.EmployeeCreateRequest;
import com.salaryai.salaryaiapi.employee.dto.EmployeePageResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeUpdateRequest;
import com.salaryai.salaryaiapi.employee.exception.DuplicateEmployeeException;
import com.salaryai.salaryaiapi.employee.exception.EmployeeNotFoundException;
import com.salaryai.salaryaiapi.employee.exception.InvalidEmployeeRequestException;
import com.salaryai.salaryaiapi.employee.exception.StaleEmployeeVersionException;
import com.salaryai.salaryaiapi.organization.Organization;
import com.salaryai.salaryaiapi.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final LocalDate JOINING_DATE = LocalDate.of(2024, 2, 12);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void createNormalizesFieldsAndUsesAuthenticatedOrganizationId() {
        Organization organization = organization();
        when(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                ORG_ID, "EMP-001")).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmail(
                ORG_ID, "ada@example.com")).thenReturn(false);
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(organization);
        stubSaveReturnsArgument();

        EmployeeResponse response = employeeService.create(ORG_ID, new EmployeeCreateRequest(
                "  emp-001  ",
                "  Ada  ",
                "  Lovelace ",
                "  Ada@Example.COM ",
                "  555-0100  ",
                "  Engineering  ",
                "  Engineer  ",
                JOINING_DATE
        ));

        assertThat(response.employeeCode()).isEqualTo("EMP-001");
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
        assertThat(response.workEmail()).isEqualTo("ada@example.com");
        assertThat(response.phone()).isEqualTo("555-0100");
        assertThat(response.department()).isEqualTo("Engineering");
        assertThat(response.designation()).isEqualTo("Engineer");
        assertThat(response.employmentStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).saveAndFlushScoped(captor.capture(), eq(ORG_ID));
        assertThat(captor.getValue().getOrganization()).isSameAs(organization);
        verify(organizationRepository).getReferenceById(ORG_ID);
    }

    @Test
    void createRejectsDuplicateEmployeeCodeBeforeSaving() {
        when(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                ORG_ID, "EMP-DUP")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(ORG_ID, createRequest("EMP-DUP")))
                .isInstanceOf(DuplicateEmployeeException.class);

        verify(employeeRepository, never()).saveAndFlushScoped(any(), anyLong());
    }

    @Test
    void createTranslatesRecognizedDuplicateConstraintToConflict() {
        Organization organization = organization();
        when(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                ORG_ID, "EMP-001")).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmail(
                ORG_ID, "ada@example.com")).thenReturn(false);
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(organization);
        when(employeeRepository.saveAndFlushScoped(any(Employee.class), eq(ORG_ID)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key violates uk_employees_org_email"));

        assertThatThrownBy(() -> employeeService.create(ORG_ID, createRequest("EMP-001")))
                .isInstanceOf(DuplicateEmployeeException.class);
    }

    @Test
    void createTranslatesUniqueViolationSqlStateToConflict() {
        Organization organization = organization();
        when(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                ORG_ID, "EMP-001")).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmail(
                ORG_ID, "ada@example.com")).thenReturn(false);
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(organization);
        when(employeeRepository.saveAndFlushScoped(any(Employee.class), eq(ORG_ID)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement", new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> employeeService.create(ORG_ID, createRequest("EMP-001")))
                .isInstanceOf(DuplicateEmployeeException.class);
    }

    @Test
    void createRethrowsUnrelatedDatabaseFailure() {
        Organization organization = organization();
        when(employeeRepository.existsByOrganizationIdAndEmployeeCode(
                ORG_ID, "EMP-001")).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmail(
                ORG_ID, "ada@example.com")).thenReturn(false);
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(organization);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("connection reset by peer");
        when(employeeRepository.saveAndFlushScoped(any(Employee.class), eq(ORG_ID)))
                .thenThrow(failure);

        assertThatThrownBy(() -> employeeService.create(ORG_ID, createRequest("EMP-001")))
                .isSameAs(failure);
    }

    @Test
    void createRejectsValueThatExceedsLimitAfterNormalization() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                "ß".repeat(50),
                "Ada",
                "Lovelace",
                "ada@example.com",
                null,
                null,
                null,
                JOINING_DATE);

        assertThatThrownBy(() -> employeeService.create(ORG_ID, request))
                .isInstanceOf(InvalidEmployeeRequestException.class)
                .hasMessage("Employee code must not exceed 50 characters.");
    }

    @Test
    void listRejectsUnsafeSortField() {
        assertThatThrownBy(() -> employeeService.list(
                ORG_ID, 0, 20, "passwordHash", "asc", null, null))
                .isInstanceOf(InvalidEmployeeRequestException.class)
                .hasMessage("Invalid sort field.");
    }

    @Test
    void listRejectsOversizedPage() {
        assertThatThrownBy(() -> employeeService.list(
                ORG_ID, 0, 101, "employeeCode", "asc", null, null))
                .isInstanceOf(InvalidEmployeeRequestException.class)
                .hasMessage("Page size must be between 1 and 100.");
    }

    @Test
    void listRejectsVeryLargePageIndex() {
        assertThatThrownBy(() -> employeeService.list(
                ORG_ID, EmployeeService.MAX_PAGE_NUMBER + 1, 20,
                "employeeCode", "asc", null, null))
                .isInstanceOf(InvalidEmployeeRequestException.class)
                .hasMessage("Page index is too large.");
    }

    @Test
    void listRejectsOversizedSearchText() {
        assertThatThrownBy(() -> employeeService.list(
                ORG_ID, 0, 20, "employeeCode", "asc", "x".repeat(101), null))
                .isInstanceOf(InvalidEmployeeRequestException.class)
                .hasMessage("Search text must not exceed 100 characters.");
    }

    @Test
    void listUsesWhitelistedSortAndNormalizedFilters() {
        Employee employee = employee(
                EMPLOYEE_ID,
                3L,
                organization(),
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.ACTIVE);
        when(employeeRepository.findByOrganizationIdWithFilters(
                eq(ORG_ID), anyString(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(employee), PageRequest.of(2, 10), 21));

        EmployeePageResponse response = employeeService.list(
                ORG_ID,
                2,
                10,
                "last-name",
                "DESC",
                "  Ada_100%  ",
                " active ");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EmploymentStatus> statusCaptor =
                ArgumentCaptor.forClass(EmploymentStatus.class);
        verify(employeeRepository).findByOrganizationIdWithFilters(
                eq(ORG_ID),
                searchCaptor.capture(),
                statusCaptor.capture(),
                pageableCaptor.capture());

        assertThat(searchCaptor.getValue()).isEqualTo("%ada\\_100\\%%");
        assertThat(statusCaptor.getValue()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("lastName"))
                .isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("lastName").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void getMissingEmployeeThrowsNotFound() {
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.get(ORG_ID, 99L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void updateRejectsStaleVersionBeforeMutating() {
        Employee employee = employee(
                EMPLOYEE_ID,
                5L,
                organization(),
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.ACTIVE);
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> employeeService.update(
                ORG_ID, EMPLOYEE_ID, updateRequest(4L, "EMP-002", "new@example.com")))
                .isInstanceOf(StaleEmployeeVersionException.class);

        verify(employeeRepository, never()).saveAndFlushScoped(any(), anyLong());
        assertThat(employee.getEmployeeCode()).isEqualTo("EMP-001");
    }

    @Test
    void updateNormalizesMutableFieldsAndPreservesImmutableFields() {
        Organization organization = organization();
        Employee employee = employee(
                EMPLOYEE_ID,
                5L,
                organization,
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.ACTIVE);
        LocalDateTime originalCreatedAt = employee.getCreatedAt();
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));
        when(employeeRepository.existsByOrganizationIdAndEmployeeCodeAndIdNot(
                ORG_ID, "EMP-002", EMPLOYEE_ID)).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                ORG_ID, "new@example.com", EMPLOYEE_ID)).thenReturn(false);
        stubSaveReturnsArgument();

        EmployeeResponse response = employeeService.update(
                ORG_ID,
                EMPLOYEE_ID,
                updateRequest(5L, "  emp-002 ", " New@Example.COM "));

        assertThat(response.employeeCode()).isEqualTo("EMP-002");
        assertThat(response.workEmail()).isEqualTo("new@example.com");
        assertThat(response.employmentStatus()).isEqualTo("ACTIVE");
        assertThat(response.createdAt()).isEqualTo(originalCreatedAt);
        assertThat(response.deactivatedAt()).isNull();
        assertThat(employee.getId()).isEqualTo(EMPLOYEE_ID);
        assertThat(employee.getOrganization()).isSameAs(organization);
        assertThat(employee.getVersion()).isEqualTo(5L);

        verify(employeeRepository).saveAndFlushScoped(employee, ORG_ID);
    }

    @Test
    void updateTranslatesOptimisticLockFailureToConflict() {
        Employee employee = employee(
                EMPLOYEE_ID,
                5L,
                organization(),
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.ACTIVE);
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));
        when(employeeRepository.existsByOrganizationIdAndEmployeeCodeAndIdNot(
                ORG_ID, "EMP-002", EMPLOYEE_ID)).thenReturn(false);
        when(employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                ORG_ID, "new@example.com", EMPLOYEE_ID)).thenReturn(false);
        when(employeeRepository.saveAndFlushScoped(employee, ORG_ID))
                .thenThrow(new ObjectOptimisticLockingFailureException(Employee.class, EMPLOYEE_ID));

        assertThatThrownBy(() -> employeeService.update(
                ORG_ID, EMPLOYEE_ID, updateRequest(5L, "EMP-002", "new@example.com")))
                .isInstanceOf(StaleEmployeeVersionException.class);
    }

    @Test
    void deactivatePersistsInactiveStatusAndTimestamp() {
        Employee employee = employee(
                EMPLOYEE_ID,
                5L,
                organization(),
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.ACTIVE);
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));
        stubSaveReturnsArgument();

        EmployeeResponse response = employeeService.deactivate(ORG_ID, EMPLOYEE_ID);

        assertThat(response.employmentStatus()).isEqualTo("INACTIVE");
        assertThat(response.deactivatedAt()).isNotNull();
        verify(employeeRepository).saveAndFlushScoped(employee, ORG_ID);
    }

    @Test
    void deactivateIsIdempotentForInactiveEmployee() {
        Employee employee = employee(
                EMPLOYEE_ID,
                5L,
                organization(),
                "EMP-001",
                "ada@example.com",
                EmploymentStatus.INACTIVE);
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, EMPLOYEE_ID))
                .thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.deactivate(ORG_ID, EMPLOYEE_ID);

        assertThat(response.employmentStatus()).isEqualTo("INACTIVE");
        assertThat(response.deactivatedAt()).isNotNull();
        verify(employeeRepository, never()).saveAndFlushScoped(any(), anyLong());
    }

    @Test
    void deactivateMissingEmployeeThrowsNotFound() {
        when(employeeRepository.findByOrganizationIdAndId(ORG_ID, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.deactivate(ORG_ID, 99L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    private void stubSaveReturnsArgument() {
        when(employeeRepository.saveAndFlushScoped(any(Employee.class), eq(ORG_ID)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Organization organization() {
        return mock(Organization.class);
    }

    private static EmployeeCreateRequest createRequest(String employeeCode) {
        return new EmployeeCreateRequest(
                employeeCode,
                "Ada",
                "Lovelace",
                "ada@example.com",
                "555-0100",
                "Engineering",
                "Engineer",
                JOINING_DATE
        );
    }

    private static EmployeeUpdateRequest updateRequest(
            Long version, String employeeCode, String workEmail) {

        return new EmployeeUpdateRequest(
                employeeCode,
                "Ada",
                "Lovelace",
                workEmail,
                "555-0100",
                "Engineering",
                "Engineer",
                JOINING_DATE,
                version
        );
    }

    private static Employee employee(
            Long id,
            Long version,
            Organization organization,
            String employeeCode,
            String workEmail,
            EmploymentStatus status) {

        Employee employee = new Employee(
                organization,
                employeeCode,
                "Ada",
                "Lovelace",
                workEmail,
                JOINING_DATE);
        ReflectionTestUtils.setField(employee, "id", id);
        ReflectionTestUtils.setField(employee, "version", version);
        ReflectionTestUtils.setField(employee, "createdAt", LocalDateTime.of(2024, 1, 1, 9, 0));
        ReflectionTestUtils.setField(employee, "updatedAt", LocalDateTime.of(2024, 1, 1, 9, 0));
        employee.setPhone("555-0100");
        employee.setDepartment("Engineering");
        employee.setDesignation("Engineer");

        if (status == EmploymentStatus.INACTIVE) {
            employee.deactivate();
            ReflectionTestUtils.setField(
                    employee, "deactivatedAt", LocalDateTime.of(2024, 3, 1, 9, 0));
        }

        return employee;
    }
}
