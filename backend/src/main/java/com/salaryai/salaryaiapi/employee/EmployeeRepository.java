package com.salaryai.salaryaiapi.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Organization-scoped persistence for {@link Employee}.
 *
 * <p>Security contract: all employee business logic, including any future
 * EmployeeService, must always supply the authenticated organization id and
 * use one of the explicitly scoped methods below. Inherited
 * {@link JpaRepository} methods such as {@code findById}, {@code findAll},
 * {@code save} and {@code deleteById} are unscoped and MUST NOT be used by
 * employee business logic.</p>
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByOrganizationIdAndId(Long organizationId, Long employeeId);

    List<Employee> findAllByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndEmployeeCode(Long organizationId, String employeeCode);

    boolean existsByOrganizationIdAndWorkEmail(Long organizationId, String workEmail);

    boolean existsByOrganizationIdAndWorkEmailAndIdNot(
            Long organizationId, String workEmail, Long employeeId);
}
