package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException;
import com.salaryai.salaryaiapi.employee.Employee;
import com.salaryai.salaryaiapi.employee.EmployeeRepository;
import com.salaryai.salaryaiapi.employee.EmploymentStatus;
import com.salaryai.salaryaiapi.organization.Organization;
import com.salaryai.salaryaiapi.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared tenant lookups, timezone handling and attendance arithmetic. */
@Service
@Transactional
public class AttendanceDomainSupport {

    private static final Set<String> DAY_CODES = Set.of(
            "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final EmployeeRepository employeeRepository;
    private final OrganizationRepository organizationRepository;
    private final AttendanceSettingsRepository settingsRepository;
    private final AttendanceScheduleRepository scheduleRepository;
    private final EmployeeScheduleAssignmentRepository assignmentRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceAuditRecordRepository auditRecordRepository;

    public AttendanceDomainSupport(
            EmployeeRepository employeeRepository,
            OrganizationRepository organizationRepository,
            AttendanceSettingsRepository settingsRepository,
            AttendanceScheduleRepository scheduleRepository,
            EmployeeScheduleAssignmentRepository assignmentRepository,
            LeaveTypeRepository leaveTypeRepository,
            AttendanceAuditRecordRepository auditRecordRepository) {

        this.employeeRepository = employeeRepository;
        this.organizationRepository = organizationRepository;
        this.settingsRepository = settingsRepository;
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditRecordRepository = auditRecordRepository;
    }

    /** Effective schedule for the employee on a work date. Never falls back across tenants. */
    public record ScheduleWindow(
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            Set<DayOfWeek> workingDays,
            boolean overnight,
            boolean assigned) {
    }

    /** Result of pairing immutable events for one work date. */
    public record CalculationResult(
            boolean hasEvents,
            AttendanceStatus status,
            Instant firstCheckIn,
            Instant lastCheckOut,
            long workedMinutes,
            long lateMinutes,
            int breakMinutes) {
    }

    public Organization organizationRef(Long organizationId) {
        return organizationRepository.getReferenceById(organizationId);
    }

    public Employee requireEmployee(Long organizationId, Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new InvalidAttendanceRequestException("Employee is required.");
        }
        return employeeRepository.findByOrganizationIdAndId(organizationId, employeeId)
                .orElseThrow(AttendanceNotFoundException::new);
    }

    public Employee requireActiveEmployee(Long organizationId, Long employeeId) {
        Employee employee = requireEmployee(organizationId, employeeId);
        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException(
                    "Attendance activity is not allowed for an inactive employee.");
        }
        return employee;
    }

    public AttendanceSettings requireSettings(Long organizationId) {
        return settingsRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new InvalidAttendanceRequestException(
                        "Attendance settings are not configured for this organization."));
    }

    public AttendanceSchedule requireSchedule(Long organizationId, Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new InvalidAttendanceRequestException("Schedule is required.");
        }
        return scheduleRepository.findByIdAndOrganizationId(scheduleId, organizationId)
                .orElseThrow(AttendanceNotFoundException::new);
    }

    public LeaveType requireLeaveType(Long organizationId, Long leaveTypeId) {
        if (leaveTypeId == null || leaveTypeId <= 0) {
            throw new InvalidAttendanceRequestException("Leave type is required.");
        }
        return leaveTypeRepository.findByIdAndOrganizationId(leaveTypeId, organizationId)
                .orElseThrow(AttendanceNotFoundException::new);
    }

    public ZoneId requireZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            throw new InvalidAttendanceRequestException("Organization timezone is required.");
        }
        try {
            if (!ZoneId.getAvailableZoneIds().contains(timeZoneId)) {
                throw new InvalidAttendanceRequestException("Invalid IANA timezone identifier.");
            }
            return ZoneId.of(timeZoneId);
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidAttendanceRequestException invalid) {
                throw invalid;
            }
            throw new InvalidAttendanceRequestException("Invalid IANA timezone identifier.");
        }
    }

    public Set<DayOfWeek> parseWorkingDays(String workingDays) {
        if (workingDays == null || workingDays.isBlank()) {
            throw new InvalidAttendanceRequestException("Working days are required.");
        }
        String[] tokens = workingDays.split(",");
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String raw : tokens) {
            String token = raw.strip().toUpperCase(Locale.ROOT);
            if (!DAY_CODES.contains(token)) {
                throw new InvalidAttendanceRequestException("Invalid working day.");
            }
            days.add(switch (token) {
                case "MON" -> DayOfWeek.MONDAY;
                case "TUE" -> DayOfWeek.TUESDAY;
                case "WED" -> DayOfWeek.WEDNESDAY;
                case "THU" -> DayOfWeek.THURSDAY;
                case "FRI" -> DayOfWeek.FRIDAY;
                case "SAT" -> DayOfWeek.SATURDAY;
                default -> DayOfWeek.SUNDAY;
            });
        }
        if (days.isEmpty()) {
            throw new InvalidAttendanceRequestException("Working days are required.");
        }
        return days;
    }

    public String normalizeWorkingDays(String workingDays) {
        Set<DayOfWeek> days = parseWorkingDays(workingDays);
        List<String> ordered = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            String code = day.name().substring(0, 3);
            if (days.contains(day)) {
                ordered.add(code);
            }
        }
        return String.join(",", ordered);
    }

    public boolean isWorkingDay(LocalDate date, String workingDays) {
        return parseWorkingDays(workingDays).contains(date.getDayOfWeek());
    }

    public void validateVersion(Long expected, long actual) {
        if (expected == null || expected != actual) {
            throw new StaleAttendanceVersionException();
        }
    }

    public ScheduleWindow resolveWindow(
            Long organizationId, Long employeeId, LocalDate workDate, AttendanceSettings settings) {

        List<EmployeeScheduleAssignment> assignments =
                assignmentRepository.findEffectiveAssignments(organizationId, employeeId, workDate);

        if (!assignments.isEmpty()) {
            AttendanceSchedule schedule = assignments.get(0).getSchedule();
            return new ScheduleWindow(
                    schedule.getName(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getBreakMinutes(),
                    parseWorkingDays(schedule.getWorkingDays()),
                    schedule.isOvernight(),
                    true);
        }

        return new ScheduleWindow(
                "Organization default",
                settings.getDefaultStartTime(),
                settings.getDefaultEndTime(),
                settings.getDefaultBreakMinutes(),
                parseWorkingDays(settings.getDefaultWorkingDays()),
                false,
                false);
    }

    /**
     * Resolves the work date in the organization timezone. For overnight shifts,
     * early-morning punches belong to the previous calendar date.
     */
    public LocalDate resolveWorkDate(Instant occurredAt, ZoneId zone, ScheduleWindow window) {
        ZonedDateTime local = occurredAt.atZone(zone);
        if (window.overnight()
                && local.toLocalTime().isBefore(LocalTime.NOON)
                && !window.startTime().isBefore(LocalTime.NOON)) {
            return local.toLocalDate().minusDays(1);
        }
        return local.toLocalDate();
    }

    public Instant scheduledStart(LocalDate workDate, ScheduleWindow window, ZoneId zone) {
        return workDate.atTime(window.startTime()).atZone(zone).toInstant();
    }

    public Instant scheduledEnd(LocalDate workDate, ScheduleWindow window, ZoneId zone) {
        LocalDate endDate = workDate;
        if (window.endTime().isBefore(window.startTime())
                || (window.endTime().equals(window.startTime()) && window.overnight())) {
            endDate = workDate.plusDays(1);
        }
        return endDate.atTime(window.endTime()).atZone(zone).toInstant();
    }

    /** Pairs punches chronologically. Missing check-outs remain INCOMPLETE; no time is invented. */
    public CalculationResult calculate(
            List<AttendanceEvent> events,
            ScheduleWindow window,
            LocalDate workDate,
            ZoneId zone,
            int gracePeriodMinutes) {

        List<AttendanceEvent> ordered = new ArrayList<>(events);
        ordered.sort((left, right) -> {
            int byTime = left.getOccurredAt().compareTo(right.getOccurredAt());
            if (byTime != 0) {
                return byTime;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null || rightId == null) {
                return leftId == null ? (rightId == null ? 0 : 1) : -1;
            }
            return leftId.compareTo(rightId);
        });

        if (ordered.isEmpty()) {
            return new CalculationResult(false, null, null, null, 0, 0, window.breakMinutes());
        }

        Instant firstCheckIn = null;
        Instant lastCheckOut = null;
        Instant openCheckIn = null;
        long workedSeconds = 0;
        int pairs = 0;

        for (AttendanceEvent event : ordered) {
            if (event.getEventType() == AttendanceEventType.CHECK_IN) {
                if (firstCheckIn == null) {
                    firstCheckIn = event.getOccurredAt();
                }
                openCheckIn = event.getOccurredAt();
                continue;
            }
            lastCheckOut = event.getOccurredAt();
            if (openCheckIn != null) {
                long seconds = Math.max(0, Duration.between(openCheckIn, event.getOccurredAt()).getSeconds());
                long breakSeconds = (long) window.breakMinutes() * 60;
                workedSeconds += Math.max(0, seconds - breakSeconds);
                pairs++;
                openCheckIn = null;
            }
        }

        Instant expectedStart = scheduledStart(workDate, window, zone);
        long lateMinutes = 0;
        if (firstCheckIn != null) {
            long lateSeconds = Duration.between(
                    expectedStart.plus(Duration.ofMinutes(gracePeriodMinutes)),
                    firstCheckIn).getSeconds();
            lateMinutes = Math.max(0, (lateSeconds + 59) / 60);
        }

        AttendanceStatus status;
        if (openCheckIn != null || pairs == 0) {
            status = AttendanceStatus.INCOMPLETE;
        } else if (lateMinutes > 0) {
            status = AttendanceStatus.LATE;
        } else {
            long scheduledMinutes = Duration.between(
                    expectedStart, scheduledEnd(workDate, window, zone)).toMinutes()
                    - window.breakMinutes();
            status = workedMinutes(workedSeconds) < scheduledMinutes / 2
                    ? AttendanceStatus.HALF_DAY
                    : AttendanceStatus.PRESENT;
        }

        return new CalculationResult(
                true,
                status,
                firstCheckIn,
                lastCheckOut,
                workedMinutes(workedSeconds),
                lateMinutes,
                window.breakMinutes());
    }

    private static long workedMinutes(long seconds) {
        return (seconds + 59) / 60;
    }

    public void audit(
            Long organizationId,
            String entityType,
            Long entityId,
            AuditAction action,
            String actor,
            String detail) {

        AttendanceAuditRecord record = new AttendanceAuditRecord(
                organizationRef(organizationId),
                entityType,
                entityId,
                action,
                actor == null || actor.isBlank() ? "organization-admin" : actor,
                detail == null ? "" : detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        auditRecordRepository.save(record);
    }

    public void validateScheduleTimes(
            LocalTime startTime, LocalTime endTime, int breakMinutes, boolean overnight) {

        if (startTime == null || endTime == null) {
            throw new InvalidAttendanceRequestException("Start time and end time are required.");
        }
        if (!overnight && !endTime.isAfter(startTime)) {
            throw new InvalidAttendanceRequestException(
                    "End time must be after start time unless the schedule is overnight.");
        }
        if (breakMinutes < 0) {
            throw new InvalidAttendanceRequestException("Break duration must not be negative.");
        }
    }

    /** Used by correction history and audit lookups to avoid accidental cross-tenant ids. */
    public void requireNonNullId(Long id) {
        if (id == null || id <= 0) {
            throw new AttendanceNotFoundException();
        }
    }

    /** Kept for callers that need the full day code list for validation messages. */
    public static List<String> supportedDayCodes() {
        return Arrays.asList("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    }
}
