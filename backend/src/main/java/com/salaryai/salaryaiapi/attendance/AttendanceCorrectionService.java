package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.CorrectionRequest;
import com.salaryai.salaryaiapi.attendance.dto.CorrectionResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Authorized corrections that preserve the original state in an append-only record. */
@Service
@Transactional
public class AttendanceCorrectionService {

    private final AttendanceCorrectionRepository correctionRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final AttendanceDomainSupport support;

    public AttendanceCorrectionService(
            AttendanceCorrectionRepository correctionRepository,
            DailyAttendanceRepository dailyAttendanceRepository,
            AttendanceDomainSupport support) {

        this.correctionRepository = correctionRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.support = support;
    }

    public CorrectionResponse correct(
            Long organizationId, String actor, CorrectionRequest request) {

        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new InvalidAttendanceRequestException("A correction reason is required.");
        }
        AttendanceCorrectionPayload payload = validateTimes(request);

        DailyAttendance daily = dailyAttendanceRepository
                .findByIdAndOrganizationId(request.dailyAttendanceId(), organizationId)
                .orElseThrow(com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException::new);

        support.validateVersion(request.expectedVersion(), daily.getVersion());

        AttendanceSettings settings = support.requireSettings(organizationId);
        boolean payrollLocked = settings.getPayrollLockThrough() != null
                && !daily.getWorkDate().isAfter(settings.getPayrollLockThrough());
        if (payrollLocked) {
            throw new AttendanceConflictException(
                    "Attendance for this work date is payroll locked.");
        }

        AttendanceStatus previousStatus = daily.getStatus();
        Instant previousCheckIn = daily.getFirstCheckIn();
        Instant previousCheckOut = daily.getLastCheckOut();

        daily.applyCalculation(
                request.correctedStatus(),
                daily.getScheduledStart(),
                daily.getScheduledEnd(),
                payload.checkIn() == null ? daily.getFirstCheckIn() : payload.checkIn(),
                payload.checkOut() == null ? daily.getLastCheckOut() : payload.checkOut(),
                daily.getWorkedMinutes(),
                daily.getLateMinutes(),
                daily.getBreakMinutes());

        DailyAttendance savedDaily;
        try {
            savedDaily = dailyAttendanceRepository.saveAndFlush(daily);
        } catch (OptimisticLockingFailureException exception) {
            throw new com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException();
        }

        AttendanceCorrection correction = new AttendanceCorrection(
                support.organizationRef(organizationId),
                savedDaily,
                savedDaily.getEmployee(),
                request.reason(),
                previousStatus,
                request.correctedStatus(),
                previousCheckIn,
                previousCheckOut,
                savedDaily.getFirstCheckIn(),
                savedDaily.getLastCheckOut(),
                request.expectedVersion(),
                actor,
                payrollLocked);

        AttendanceCorrection saved = correctionRepository.saveAndFlush(correction);
        support.audit(organizationId, "ATTENDANCE_CORRECTION", saved.getId(),
                AuditAction.CORRECTION_APPLIED, actor,
                "Corrected " + daily.getWorkDate() + " from " + previousStatus
                        + " to " + request.correctedStatus() + ".");
        return CorrectionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CorrectionResponse> list(
            Long organizationId, Long dailyAttendanceId, int page, int size) {

        AttendanceRequestValidator.page(page, size);
        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "correctedAt"));
        Page<CorrectionResponse> mapped = (dailyAttendanceId == null
                ? correctionRepository.findAllByOrganizationIdOrderByCorrectedAtDesc(
                        organizationId, pageable)
                : correctionRepository
                        .findAllByOrganizationIdAndDailyAttendanceIdOrderByCorrectedAtDesc(
                                organizationId, dailyAttendanceId, pageable))
                .map(CorrectionResponse::from);
        return new PageResponse<>(
                mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements(), mapped.getTotalPages(), mapped.isFirst(), mapped.isLast());
    }

    private record AttendanceCorrectionPayload(Instant checkIn, Instant checkOut) {
    }

    private static AttendanceCorrectionPayload validateTimes(CorrectionRequest request) {
        if (request.correctedCheckIn() != null
                && request.correctedCheckOut() != null
                && request.correctedCheckOut().isBefore(request.correctedCheckIn())) {
            throw new InvalidAttendanceRequestException(
                    "Corrected check-out must not be before corrected check-in.");
        }
        return new AttendanceCorrectionPayload(
                request.correctedCheckIn(), request.correctedCheckOut());
    }
}
