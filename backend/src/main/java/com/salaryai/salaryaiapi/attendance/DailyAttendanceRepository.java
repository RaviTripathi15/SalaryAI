package com.salaryai.salaryaiapi.attendance;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Organization-scoped authoritative daily attendance access. */
public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, Long> {

    Optional<DailyAttendance> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<DailyAttendance> findByOrganizationIdAndEmployeeIdAndWorkDate(
            Long organizationId, Long employeeId, LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM DailyAttendance d
            WHERE d.organization.id = :organizationId
              AND d.employee.id = :employeeId
              AND d.workDate = :workDate
            """)
    Optional<DailyAttendance> findForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("workDate") LocalDate workDate);

    @Query(value = """
            SELECT d FROM DailyAttendance d
            WHERE d.organization.id = :organizationId
              AND (:employeeId IS NULL OR d.employee.id = :employeeId)
              AND (:fromDate IS NULL OR d.workDate >= :fromDate)
              AND (:toDate IS NULL OR d.workDate <= :toDate)
              AND (:status IS NULL OR d.status = :status)
            """, countQuery = """
            SELECT COUNT(d) FROM DailyAttendance d
            WHERE d.organization.id = :organizationId
              AND (:employeeId IS NULL OR d.employee.id = :employeeId)
              AND (:fromDate IS NULL OR d.workDate >= :fromDate)
              AND (:toDate IS NULL OR d.workDate <= :toDate)
              AND (:status IS NULL OR d.status = :status)
            """)
    Page<DailyAttendance> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") AttendanceStatus status,
            Pageable pageable);

    List<DailyAttendance> findAllByOrganizationIdAndWorkDate(
            Long organizationId, LocalDate workDate);

    Page<DailyAttendance> findAllByOrganizationIdAndWorkDate(
            Long organizationId, LocalDate workDate, Pageable pageable);

    @Query("""
            SELECT d.status AS status, COUNT(d) AS total
            FROM DailyAttendance d
            WHERE d.organization.id = :organizationId
              AND (:employeeId IS NULL OR d.employee.id = :employeeId)
              AND d.workDate >= :fromDate
              AND d.workDate <= :toDate
            GROUP BY d.status
            """)
    List<StatusCount> countByStatus(
            @Param("organizationId") Long organizationId,
            @Param("employeeId") Long employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    interface StatusCount {
        AttendanceStatus getStatus();

        long getTotal();
    }
}
