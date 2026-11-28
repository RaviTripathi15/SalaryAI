package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Organization-scoped employee schedule assignment access. */
public interface EmployeeScheduleAssignmentRepository
        extends JpaRepository<EmployeeScheduleAssignment, Long> {

    Optional<EmployeeScheduleAssignment> findByIdAndOrganizationId(Long id, Long organizationId);

    List<EmployeeScheduleAssignment> findAllByOrganizationIdAndEmployeeIdOrderByEffectiveFromDesc(
            Long organizationId, Long employeeId);

    List<EmployeeScheduleAssignment> findAllByOrganizationIdOrderByEffectiveFromDesc(
            Long organizationId);

    long countByScheduleIdAndOrganizationId(Long scheduleId, Long organizationId);

    @Query("""
            SELECT a FROM EmployeeScheduleAssignment a
            WHERE a.organization.id = :organizationId
              AND a.employee.id = :employeeId
              AND a.effectiveFrom <= :workDate
              AND (a.effectiveTo IS NULL OR a.effectiveTo >= :workDate)
            ORDER BY a.effectiveFrom DESC
            """)
    List<EmployeeScheduleAssignment> findEffectiveAssignments(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("workDate") LocalDate workDate);

    @Query("""
            SELECT COUNT(a) FROM EmployeeScheduleAssignment a
            WHERE a.organization.id = :organizationId
              AND a.employee.id = :employeeId
              AND a.effectiveFrom <= :newTo
              AND (:newFrom IS NULL OR a.effectiveTo IS NULL OR a.effectiveTo >= :newFrom)
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    long countOverlapping(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("newFrom") LocalDate newFrom,
            @Param("newTo") LocalDate newTo,
            @Param("excludeId") Long excludeId);
}
