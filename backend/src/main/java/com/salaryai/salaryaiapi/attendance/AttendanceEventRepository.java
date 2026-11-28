package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Immutable, organization-scoped attendance event access. */
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {

    Optional<AttendanceEvent> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<AttendanceEvent> findByOrganizationIdAndIdempotencyKey(
            Long organizationId, String idempotencyKey);

    /** Serializes punch processing for one employee inside the current transaction. */
    @Query(value = "SELECT pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    void acquirePunchLock(@Param("lockKey") long lockKey);

    List<AttendanceEvent> findByOrganizationIdAndEmployeeIdAndWorkDateOrderByOccurredAtAsc(
            Long organizationId, Long employeeId, LocalDate workDate);

    @Query(value = """
            SELECT e FROM AttendanceEvent e
            WHERE e.organization.id = :organizationId
              AND (:employeeId IS NULL OR e.employee.id = :employeeId)
              AND (:fromDate IS NULL OR e.workDate >= :fromDate)
              AND (:toDate IS NULL OR e.workDate <= :toDate)
            """, countQuery = """
            SELECT COUNT(e) FROM AttendanceEvent e
            WHERE e.organization.id = :organizationId
              AND (:employeeId IS NULL OR e.employee.id = :employeeId)
              AND (:fromDate IS NULL OR e.workDate >= :fromDate)
              AND (:toDate IS NULL OR e.workDate <= :toDate)
            """)
    Page<AttendanceEvent> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}
