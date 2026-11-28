package com.salaryai.salaryaiapi.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Organization-scoped persistence for {@link Employee}.
 *
 * <p>Security contract: all employee business logic, including any future
 * EmployeeService, must always supply the authenticated organization id and
 * use one of the explicitly scoped methods below. Inherited
 * {@link JpaRepository} methods such as {@code findById}, {@code findAll} and
 * {@code deleteById} are unscoped and MUST NOT be used by employee business
 * logic. The scoped {@code saveScoped} and {@code saveAndFlushScoped} wrappers
 * below are the only persistence entry points and reject entities that do not
 * belong to the supplied organization id.</p>
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByOrganizationIdAndId(Long organizationId, Long employeeId);

    List<Employee> findAllByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndEmployeeCode(Long organizationId, String employeeCode);

    boolean existsByOrganizationIdAndWorkEmail(Long organizationId, String workEmail);

    boolean existsByOrganizationIdAndEmployeeCodeAndIdNot(
            Long organizationId, String employeeCode, Long employeeId);

    boolean existsByOrganizationIdAndWorkEmailAndIdNot(
            Long organizationId, String workEmail, Long employeeId);

    @Query(value = """
            SELECT e FROM Employee e
            WHERE e.organization.id = :organizationId
              AND (:status IS NULL OR e.employmentStatus = :status)
              AND (:search IS NULL OR
                   LOWER(e.employeeCode) LIKE :search ESCAPE '\\' OR
                   LOWER(e.firstName) LIKE :search ESCAPE '\\' OR
                   LOWER(e.lastName) LIKE :search ESCAPE '\\' OR
                   LOWER(e.workEmail) LIKE :search ESCAPE '\\' OR
                   LOWER(e.department) LIKE :search ESCAPE '\\' OR
                   LOWER(e.designation) LIKE :search ESCAPE '\\')
            """, countQuery = """
            SELECT COUNT(e) FROM Employee e
            WHERE e.organization.id = :organizationId
              AND (:status IS NULL OR e.employmentStatus = :status)
              AND (:search IS NULL OR
                   LOWER(e.employeeCode) LIKE :search ESCAPE '\\' OR
                   LOWER(e.firstName) LIKE :search ESCAPE '\\' OR
                   LOWER(e.lastName) LIKE :search ESCAPE '\\' OR
                   LOWER(e.workEmail) LIKE :search ESCAPE '\\' OR
                   LOWER(e.department) LIKE :search ESCAPE '\\' OR
                   LOWER(e.designation) LIKE :search ESCAPE '\\')
            """)
    Page<Employee> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("search") String search,
            @Param("status") EmploymentStatus status,
            Pageable pageable);

    default Employee saveScoped(Employee employee, Long organizationId) {
        requireOrganization(employee, organizationId);
        return save(employee);
    }

    default Employee saveAndFlushScoped(Employee employee, Long organizationId) {
        requireOrganization(employee, organizationId);
        return saveAndFlush(employee);
    }

    private static void requireOrganization(Employee employee, Long organizationId) {
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        if (employee.getOrganization() == null
                || !organizationId.equals(employee.getOrganization().getId())) {
            throw new IllegalStateException(
                    "Employee does not belong to the authenticated organization.");
        }
    }
}
