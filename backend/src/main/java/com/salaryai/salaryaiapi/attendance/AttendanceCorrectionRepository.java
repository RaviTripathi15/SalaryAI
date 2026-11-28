package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Organization-scoped correction history access. */
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    Optional<AttendanceCorrection> findByIdAndOrganizationId(Long id, Long organizationId);

    List<AttendanceCorrection> findAllByOrganizationIdAndDailyAttendanceIdOrderByCorrectedAtDesc(
            Long organizationId, Long dailyAttendanceId);

    Page<AttendanceCorrection> findAllByOrganizationIdAndDailyAttendanceIdOrderByCorrectedAtDesc(
            Long organizationId, Long dailyAttendanceId, Pageable pageable);

    Page<AttendanceCorrection> findAllByOrganizationIdOrderByCorrectedAtDesc(
            Long organizationId, Pageable pageable);
}
