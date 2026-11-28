package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Organization-scoped leave request access. */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Optional<LeaveRequest> findByIdAndOrganizationId(Long id, Long organizationId);

    @Query(value = """
            SELECT l FROM LeaveRequest l
            WHERE l.organization.id = :organizationId
              AND (:employeeId IS NULL OR l.employee.id = :employeeId)
              AND (:status IS NULL OR l.status = :status)
              AND (:fromDate IS NULL OR l.endDate >= :fromDate)
              AND (:toDate IS NULL OR l.startDate <= :toDate)
            """, countQuery = """
            SELECT COUNT(l) FROM LeaveRequest l
            WHERE l.organization.id = :organizationId
              AND (:employeeId IS NULL OR l.employee.id = :employeeId)
              AND (:status IS NULL OR l.status = :status)
              AND (:fromDate IS NULL OR l.endDate >= :fromDate)
              AND (:toDate IS NULL OR l.startDate <= :toDate)
            """)
    Page<LeaveRequest> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            SELECT l FROM LeaveRequest l
            WHERE l.organization.id = :organizationId
              AND l.employee.id = :employeeId
              AND l.status IN ('PENDING', 'APPROVED')
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND (:excludeId IS NULL OR l.id <> :excludeId)
            """)
    List<LeaveRequest> findActiveOverlaps(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    @Query("""
            SELECT COUNT(l) FROM LeaveRequest l
            WHERE l.organization.id = :organizationId
              AND l.employee.id = :employeeId
              AND l.status = 'APPROVED'
              AND l.startDate <= :workDate
              AND l.endDate >= :workDate
            """)
    long countApprovedOnDate(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("workDate") LocalDate workDate);
}
