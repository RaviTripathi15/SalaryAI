package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.employee.dto.EmployeeCreateRequest;
import com.salaryai.salaryaiapi.employee.dto.EmployeePageResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeSortField;
import com.salaryai.salaryaiapi.employee.dto.EmployeeUpdateRequest;
import com.salaryai.salaryaiapi.employee.exception.DuplicateEmployeeException;
import com.salaryai.salaryaiapi.employee.exception.EmployeeNotFoundException;
import com.salaryai.salaryaiapi.employee.exception.InvalidEmployeeRequestException;
import com.salaryai.salaryaiapi.employee.exception.StaleEmployeeVersionException;
import com.salaryai.salaryaiapi.organization.Organization;
import com.salaryai.salaryaiapi.organization.OrganizationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class EmployeeService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_NUMBER = 1_000_000;
    public static final int MAX_SEARCH_LENGTH = 100;

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String DUPLICATE_CODE_CONSTRAINT = "uk_employees_org_code";
    private static final String DUPLICATE_EMAIL_CONSTRAINT = "uk_employees_org_email";

    private final EmployeeRepository employeeRepository;
    private final OrganizationRepository organizationRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            OrganizationRepository organizationRepository) {

        this.employeeRepository = employeeRepository;
        this.organizationRepository = organizationRepository;
    }

    public EmployeeResponse create(
            Long organizationId, EmployeeCreateRequest request) {

        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        String firstName = normalizeRequired(request.firstName(), 100, "First name");
        String lastName = normalizeRequired(request.lastName(), 100, "Last name");
        String workEmail = normalizeWorkEmail(request.workEmail());
        String phone = normalizeOptional(request.phone(), 20, "Phone number");
        String department = normalizeOptional(request.department(), 100, "Department");
        String designation = normalizeOptional(request.designation(), 100, "Designation");
        LocalDate joiningDate = requireJoiningDate(request.joiningDate());

        if (employeeRepository.existsByOrganizationIdAndEmployeeCode(
                organizationId, employeeCode)
                || employeeRepository.existsByOrganizationIdAndWorkEmail(
                organizationId, workEmail)) {
            throw new DuplicateEmployeeException();
        }

        Organization organization = organizationRepository.getReferenceById(organizationId);
        Employee employee = new Employee(
                organization,
                employeeCode,
                firstName,
                lastName,
                workEmail,
                joiningDate);

        employee.setPhone(phone);
        employee.setDepartment(department);
        employee.setDesignation(designation);

        return EmployeeResponse.from(persistScoped(employee, organizationId));
    }

    @Transactional(readOnly = true)
    public EmployeePageResponse list(
            Long organizationId,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            String search,
            String status) {

        Objects.requireNonNull(organizationId, "organizationId must not be null");

        if (page < 0) {
            throw new InvalidEmployeeRequestException("Page index must not be negative.");
        }
        if (page > MAX_PAGE_NUMBER) {
            throw new InvalidEmployeeRequestException("Page index is too large.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidEmployeeRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }

        EmployeeSortField sortField = EmployeeSortField.from(sortBy);
        if (sortField == null) {
            throw new InvalidEmployeeRequestException("Invalid sort field.");
        }

        Sort.Direction direction = parseDirection(sortDirection);
        EmploymentStatus statusFilter = parseStatus(status);
        String searchPattern = buildSearchPattern(search);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, sortField.property()));

        Page<Employee> employees = employeeRepository.findByOrganizationIdWithFilters(
                organizationId,
                searchPattern,
                statusFilter,
                pageable);

        Page<EmployeeResponse> mapped = employees.map(EmployeeResponse::from);

        return new EmployeePageResponse(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages(),
                mapped.isFirst(),
                mapped.isLast()
        );
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long organizationId, Long employeeId) {
        return EmployeeResponse.from(requireEmployee(organizationId, employeeId));
    }

    public EmployeeResponse update(
            Long organizationId,
            Long employeeId,
            EmployeeUpdateRequest request) {

        Objects.requireNonNull(request, "request must not be null");

        Employee employee = requireEmployee(organizationId, employeeId);

        if (!Objects.equals(employee.getVersion(), request.version())) {
            throw new StaleEmployeeVersionException();
        }

        String employeeCode = normalizeEmployeeCode(request.employeeCode());
        String firstName = normalizeRequired(request.firstName(), 100, "First name");
        String lastName = normalizeRequired(request.lastName(), 100, "Last name");
        String workEmail = normalizeWorkEmail(request.workEmail());
        String phone = normalizeOptional(request.phone(), 20, "Phone number");
        String department = normalizeOptional(request.department(), 100, "Department");
        String designation = normalizeOptional(request.designation(), 100, "Designation");
        LocalDate joiningDate = requireJoiningDate(request.joiningDate());

        if (employeeRepository.existsByOrganizationIdAndEmployeeCodeAndIdNot(
                organizationId, employeeCode, employeeId)
                || employeeRepository.existsByOrganizationIdAndWorkEmailAndIdNot(
                organizationId, workEmail, employeeId)) {
            throw new DuplicateEmployeeException();
        }

        employee.setEmployeeCode(employeeCode);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setWorkEmail(workEmail);
        employee.setPhone(phone);
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setJoiningDate(joiningDate);

        return EmployeeResponse.from(persistScoped(employee, organizationId));
    }

    public EmployeeResponse deactivate(Long organizationId, Long employeeId) {
        Employee employee = requireEmployee(organizationId, employeeId);

        if (employee.getEmploymentStatus() == EmploymentStatus.ACTIVE) {
            employee.deactivate();
            return EmployeeResponse.from(persistScoped(employee, organizationId));
        }

        return EmployeeResponse.from(employee);
    }

    private Employee requireEmployee(Long organizationId, Long employeeId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");

        return employeeRepository.findByOrganizationIdAndId(organizationId, employeeId)
                .orElseThrow(EmployeeNotFoundException::new);
    }

    private Employee persistScoped(Employee employee, Long organizationId) {
        try {
            return employeeRepository.saveAndFlushScoped(employee, organizationId);
        } catch (DataIntegrityViolationException exception) {
            if (isKnownDuplicateConstraint(exception)) {
                throw new DuplicateEmployeeException();
            }
            throw exception;
        } catch (OptimisticLockingFailureException exception) {
            throw new StaleEmployeeVersionException();
        }
    }

    private static boolean isKnownDuplicateConstraint(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains(DUPLICATE_CODE_CONSTRAINT)
                        || normalized.contains(DUPLICATE_EMAIL_CONSTRAINT)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static Sort.Direction parseDirection(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return Sort.Direction.ASC;
        }

        String normalized = sortDirection.strip().toUpperCase(Locale.ROOT);
        if ("ASC".equals(normalized)) {
            return Sort.Direction.ASC;
        }
        if ("DESC".equals(normalized)) {
            return Sort.Direction.DESC;
        }
        throw new InvalidEmployeeRequestException("Invalid sort direction.");
    }

    private static EmploymentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return EmploymentStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidEmployeeRequestException("Invalid employment status filter.");
        }
    }

    private static String buildSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String trimmed = search.strip();
        validateMaxLength(trimmed, MAX_SEARCH_LENGTH, "Search text");

        String escaped = trimmed
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .toLowerCase(Locale.ROOT);

        return "%" + escaped + "%";
    }

    private static String normalizeEmployeeCode(String value) {
        String normalized = normalizeRequired(value, 50, "Employee code")
                .toUpperCase(Locale.ROOT);
        validateMaxLength(normalized, 50, "Employee code");
        return normalized;
    }

    private static String normalizeWorkEmail(String value) {
        String normalized = normalizeRequired(value, 255, "Work email")
                .toLowerCase(Locale.ROOT);
        validateMaxLength(normalized, 255, "Work email");
        return normalized;
    }

    private static String normalizeRequired(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidEmployeeRequestException(fieldName + " is required.");
        }

        String trimmed = value.strip();
        validateMaxLength(trimmed, maxLength, fieldName);
        return trimmed;
    }

    private static String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.strip();
        validateMaxLength(trimmed, maxLength, fieldName);
        return trimmed;
    }

    private static void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new InvalidEmployeeRequestException(
                    fieldName + " must not exceed " + maxLength + " characters.");
        }
    }

    private static LocalDate requireJoiningDate(LocalDate joiningDate) {
        if (joiningDate == null) {
            throw new InvalidEmployeeRequestException("Joining date is required.");
        }
        return joiningDate;
    }
}
