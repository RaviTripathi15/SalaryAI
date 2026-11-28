package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.AttendanceEventResponse;
import com.salaryai.salaryaiapi.attendance.dto.DailyAttendanceResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.dto.PunchRequest;
import com.salaryai.salaryaiapi.attendance.dto.PunchResponse;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import com.salaryai.salaryaiapi.employee.Employee;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transactional check-in/check-out processing with idempotency and per-employee locking. */
@Service
@Transactional
public class AttendanceRecordingService {

    private final AttendanceEventRepository eventRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceDomainSupport support;

    public AttendanceRecordingService(
            AttendanceEventRepository eventRepository,
            DailyAttendanceRepository dailyAttendanceRepository,
            HolidayRepository holidayRepository,
            LeaveRequestRepository leaveRequestRepository,
            AttendanceDomainSupport support) {

        this.eventRepository = eventRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.support = support;
    }

    public PunchResponse checkIn(Long organizationId, String actor, PunchRequest request) {
        return punch(organizationId, actor, AttendanceEventType.CHECK_IN, request);
    }

    public PunchResponse checkOut(Long organizationId, String actor, PunchRequest request) {
        return punch(organizationId, actor, AttendanceEventType.CHECK_OUT, request);
    }

    private PunchResponse punch(
            Long organizationId,
            String actor,
            AttendanceEventType type,
            PunchRequest request) {

        Objects.requireNonNull(request, "request must not be null");
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new InvalidAttendanceRequestException("Idempotency key is required.");
        }

        // Serialize all punches for this employee, including duplicate submissions.
        eventRepository.acquirePunchLock(request.employeeId() == null ? 0L : request.employeeId());

        var existing = eventRepository.findByOrganizationIdAndIdempotencyKey(
                organizationId, request.idempotencyKey());
        if (existing.isPresent()) {
            AttendanceEvent event = existing.get();
            if (event.getEventType() != type) {
                throw new AttendanceConflictException(
                        "This idempotency key was used for a different attendance action.");
            }
            return response(event, true);
        }

        Employee employee = support.requireActiveEmployee(organizationId, request.employeeId());
        AttendanceSettings settings = support.requireSettings(organizationId);
        ZoneId zone = support.requireZone(settings.getTimeZoneId());

        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        LocalDate initialDate = occurredAt.atZone(zone).toLocalDate();
        AttendanceDomainSupport.ScheduleWindow window =
                support.resolveWindow(organizationId, employee.getId(), initialDate, settings);
        LocalDate workDate = support.resolveWorkDate(occurredAt, zone, window);
        if (!workDate.equals(initialDate)) {
            window = support.resolveWindow(organizationId, employee.getId(), workDate, settings);
        }

        List<AttendanceEvent> events = new ArrayList<>(
                eventRepository.findByOrganizationIdAndEmployeeIdAndWorkDateOrderByOccurredAtAsc(
                        organizationId, employee.getId(), workDate));

        AttendanceEvent lastEvent = events.isEmpty() ? null : events.get(events.size() - 1);
        if (type == AttendanceEventType.CHECK_IN
                && lastEvent != null
                && lastEvent.getEventType() == AttendanceEventType.CHECK_IN) {
            throw new AttendanceConflictException(
                    "This employee already has an open check-in for the work date.");
        }
        if (type == AttendanceEventType.CHECK_OUT) {
            if (lastEvent == null || lastEvent.getEventType() == AttendanceEventType.CHECK_OUT) {
                throw new AttendanceConflictException(
                        "There is no open check-in to match this check-out.");
            }
            if (occurredAt.isBefore(lastEvent.getOccurredAt())) {
                throw new AttendanceConflictException(
                        "Check-out must not occur before the matching check-in.");
            }
        }

        AttendanceEvent event = new AttendanceEvent(
                support.organizationRef(organizationId),
                employee,
                type,
                occurredAt,
                workDate,
                zone.getId(),
                request.idempotencyKey());

        AttendanceEvent saved;
        try {
            saved = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            throw new AttendanceConflictException("Duplicate attendance event request.");
        }

        events.add(saved);
        recalculate(organizationId, employee, workDate, window, zone, settings, events);

        support.audit(organizationId, "ATTENDANCE_EVENT", saved.getId(),
                type == AttendanceEventType.CHECK_IN ? AuditAction.CHECK_IN : AuditAction.CHECK_OUT,
                actor,
                type + " recorded for work date " + workDate + ".");

        return response(saved, false);
    }

    private void recalculate(
            Long organizationId,
            Employee employee,
            LocalDate workDate,
            AttendanceDomainSupport.ScheduleWindow window,
            ZoneId zone,
            AttendanceSettings settings,
            List<AttendanceEvent> events) {

        AttendanceDomainSupport.CalculationResult result = support.calculate(
                events, window, workDate, zone, settings.getGracePeriodMinutes());

        AttendanceStatus calculatedStatus = result.status();
        AttendanceStatus status;
        if (holidayRepository.findByOrganizationIdAndHolidayDate(organizationId, workDate).isPresent()) {
            status = AttendanceStatus.HOLIDAY;
        } else if (leaveRequestRepository.countApprovedOnDate(
                organizationId, employee.getId(), workDate) > 0) {
            status = AttendanceStatus.ON_LEAVE;
        } else if (!window.workingDays().contains(workDate.getDayOfWeek())) {
            status = AttendanceStatus.WEEK_OFF;
        } else {
            status = calculatedStatus;
        }

        DailyAttendance daily = dailyAttendanceRepository
                .findForUpdate(organizationId, employee.getId(), workDate)
                .orElseGet(() -> new DailyAttendance(
                        support.organizationRef(organizationId), employee, workDate, status));

        daily.applyCalculation(
                status,
                support.scheduledStart(workDate, window, zone),
                support.scheduledEnd(workDate, window, zone),
                result.firstCheckIn(),
                result.lastCheckOut(),
                Math.toIntExact(result.workedMinutes()),
                Math.toIntExact(result.lateMinutes()),
                result.breakMinutes());

        dailyAttendanceRepository.saveAndFlush(daily);
    }

    private PunchResponse response(AttendanceEvent event, boolean duplicateRequest) {
        DailyAttendanceResponse daily = dailyAttendanceRepository
                .findByOrganizationIdAndEmployeeIdAndWorkDate(
                        event.getOrganization().getId(),
                        event.getEmployee().getId(),
                        event.getWorkDate())
                .map(DailyAttendanceResponse::from)
                .orElse(null);

        return new PunchResponse(
                event.getId(),
                daily == null ? null : daily.id(),
                event.getEventType(),
                event.getEmployee().getId(),
                event.getWorkDate(),
                event.getOccurredAt(),
                event.getTimeZoneId(),
                duplicateRequest,
                daily);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceEventResponse> events(
            Long organizationId, Long employeeId, LocalDate fromDate, LocalDate toDate,
            int page, int size) {

        AttendanceRequestValidator.page(page, size);
        AttendanceRequestValidator.dateRange(fromDate, toDate);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AttendanceEvent> result = eventRepository.findByOrganizationIdWithFilters(
                organizationId, employeeId, fromDate, toDate, pageable);
        Page<AttendanceEventResponse> mapped = result.map(AttendanceEventResponse::from);
        return new PageResponse<>(
                mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements(), mapped.getTotalPages(), mapped.isFirst(), mapped.isLast());
    }
}
