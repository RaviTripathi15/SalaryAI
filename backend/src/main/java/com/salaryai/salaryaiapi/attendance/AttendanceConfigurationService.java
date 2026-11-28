package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.AttendanceSettingsRequest;
import com.salaryai.salaryaiapi.attendance.dto.AttendanceSettingsResponse;
import com.salaryai.salaryaiapi.attendance.dto.LeaveTypeRequest;
import com.salaryai.salaryaiapi.attendance.dto.LeaveTypeResponse;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleAssignmentResponse;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleRequest;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleResponse;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import com.salaryai.salaryaiapi.employee.Employee;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class AttendanceConfigurationService {

    private final AttendanceSettingsRepository settingsRepository;
    private final AttendanceScheduleRepository scheduleRepository;
    private final EmployeeScheduleAssignmentRepository assignmentRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceDomainSupport support;

    public AttendanceConfigurationService(
            AttendanceSettingsRepository settingsRepository,
            AttendanceScheduleRepository scheduleRepository,
            EmployeeScheduleAssignmentRepository assignmentRepository,
            LeaveTypeRepository leaveTypeRepository,
            AttendanceDomainSupport support) {

        this.settingsRepository = settingsRepository;
        this.scheduleRepository = scheduleRepository;
        this.assignmentRepository = assignmentRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public AttendanceSettingsResponse getSettings(Long organizationId) {
        return AttendanceSettingsResponse.from(support.requireSettings(organizationId));
    }

    public AttendanceSettingsResponse saveSettings(
            Long organizationId, String actor, AttendanceSettingsRequest request) {

        Objects.requireNonNull(request, "request must not be null");
        support.requireZone(request.timeZoneId());
        String workingDays = support.normalizeWorkingDays(request.defaultWorkingDays());
        support.validateScheduleTimes(
                request.defaultStartTime(), request.defaultEndTime(),
                request.defaultBreakMinutes(), false);
        validateDateRange(request.payrollLockThrough(), null);

        AttendanceSettings existing = settingsRepository.findByOrganizationId(organizationId)
                .orElse(null);
        AttendanceSettings settings;

        if (existing == null) {
            settings = new AttendanceSettings(
                    support.organizationRef(organizationId),
                    request.timeZoneId(),
                    request.gracePeriodMinutes(),
                    request.calculationPolicy(),
                    request.defaultStartTime(),
                    request.defaultEndTime(),
                    request.defaultBreakMinutes(),
                    workingDays);
            settings.update(
                    request.timeZoneId(),
                    request.gracePeriodMinutes(),
                    request.calculationPolicy(),
                    request.defaultStartTime(),
                    request.defaultEndTime(),
                    request.defaultBreakMinutes(),
                    workingDays,
                    request.payrollLockThrough());
        } else {
            support.validateVersion(request.version(), existing.getVersion());
            existing.update(
                    request.timeZoneId(),
                    request.gracePeriodMinutes(),
                    request.calculationPolicy(),
                    request.defaultStartTime(),
                    request.defaultEndTime(),
                    request.defaultBreakMinutes(),
                    workingDays,
                    request.payrollLockThrough());
            settings = existing;
        }

        AttendanceSettings saved = flush(() -> settingsRepository.saveAndFlush(settings));
        support.audit(organizationId, "ATTENDANCE_SETTINGS", saved.getId(),
                AuditAction.SETTINGS_UPDATED, actor, "Attendance settings saved.");
        return AttendanceSettingsResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(Long organizationId) {
        return scheduleRepository.findAllByOrganizationIdOrderByEffectiveFromDesc(organizationId)
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    public ScheduleResponse createSchedule(
            Long organizationId, String actor, ScheduleRequest request) {

        Objects.requireNonNull(request, "request must not be null");
        String workingDays = support.normalizeWorkingDays(request.workingDays());
        support.validateScheduleTimes(
                request.startTime(), request.endTime(), request.breakMinutes(), request.overnight());
        validateDateRange(request.effectiveTo(), request.effectiveFrom());
        validateName(request.name());

        AttendanceSchedule schedule = new AttendanceSchedule(
                support.organizationRef(organizationId),
                request.name(),
                request.startTime(),
                request.endTime(),
                request.breakMinutes(),
                workingDays,
                request.effectiveFrom(),
                request.effectiveTo(),
                request.active(),
                request.overnight());

        AttendanceSchedule saved = flush(() -> scheduleRepository.saveAndFlush(schedule));
        support.audit(organizationId, "ATTENDANCE_SCHEDULE", saved.getId(),
                AuditAction.SCHEDULE_CREATED, actor, "Schedule " + saved.getName() + " created.");
        return ScheduleResponse.from(saved);
    }

    public ScheduleResponse updateSchedule(
            Long organizationId, Long scheduleId, String actor, ScheduleRequest request) {

        AttendanceSchedule schedule = support.requireSchedule(organizationId, scheduleId);
        support.validateVersion(request.version(), schedule.getVersion());
        String workingDays = support.normalizeWorkingDays(request.workingDays());
        support.validateScheduleTimes(
                request.startTime(), request.endTime(), request.breakMinutes(), request.overnight());
        validateDateRange(request.effectiveTo(), request.effectiveFrom());
        validateName(request.name());

        if (scheduleRepository.existsByNameAndOrganizationIdAndIdNot(
                organizationId, request.name(), scheduleId)) {
            throw new AttendanceConflictException("A schedule with this name already exists.");
        }

        schedule.update(
                request.name(), request.startTime(), request.endTime(), request.breakMinutes(),
                workingDays, request.effectiveFrom(), request.effectiveTo(),
                request.active(), request.overnight());

        AttendanceSchedule saved = flush(() -> scheduleRepository.saveAndFlush(schedule));
        support.audit(organizationId, "ATTENDANCE_SCHEDULE", saved.getId(),
                AuditAction.SCHEDULE_UPDATED, actor, "Schedule " + saved.getName() + " updated.");
        return ScheduleResponse.from(saved);
    }

    public void deleteSchedule(Long organizationId, Long scheduleId, String actor, Long version) {
        AttendanceSchedule schedule = support.requireSchedule(organizationId, scheduleId);
        support.validateVersion(version, schedule.getVersion());
        if (assignmentRepository.countByScheduleIdAndOrganizationId(scheduleId, organizationId) > 0) {
            throw new AttendanceConflictException(
                    "A schedule with employee assignments cannot be deleted.");
        }
        scheduleRepository.delete(schedule);
        scheduleRepository.flush();
        support.audit(organizationId, "ATTENDANCE_SCHEDULE", scheduleId,
                AuditAction.SCHEDULE_DELETED, actor, "Schedule deleted.");
    }

    @Transactional(readOnly = true)
    public List<ScheduleAssignmentResponse> listAssignments(Long organizationId, Long employeeId) {
        if (employeeId == null) {
            return assignmentRepository.findAllByOrganizationIdOrderByEffectiveFromDesc(organizationId)
                    .stream().map(ScheduleAssignmentResponse::from).toList();
        }
        Employee employee = support.requireEmployee(organizationId, employeeId);
        return assignmentRepository.findAllByOrganizationIdAndEmployeeIdOrderByEffectiveFromDesc(
                        organizationId, employee.getId())
                .stream().map(ScheduleAssignmentResponse::from).toList();
    }

    public ScheduleAssignmentResponse createAssignment(
            Long organizationId,
            com.salaryai.salaryaiapi.attendance.dto.ScheduleAssignmentRequest request,
            String actor) {

        Objects.requireNonNull(request, "request must not be null");
        Employee employee = support.requireEmployee(organizationId, request.employeeId());
        AttendanceSchedule schedule = support.requireSchedule(organizationId, request.scheduleId());
        validateDateRange(request.effectiveTo(), request.effectiveFrom());
        validateNoOverlap(
                organizationId, employee.getId(), request.effectiveFrom(), request.effectiveTo(), null);

        EmployeeScheduleAssignment assignment = new EmployeeScheduleAssignment(
                support.organizationRef(organizationId), employee, schedule,
                request.effectiveFrom(), request.effectiveTo());

        EmployeeScheduleAssignment saved =
                flush(() -> assignmentRepository.saveAndFlush(assignment));
        support.audit(organizationId, "SCHEDULE_ASSIGNMENT", saved.getId(),
                AuditAction.ASSIGNMENT_CREATED, actor,
                "Schedule assigned to employee " + employee.getId() + ".");
        return ScheduleAssignmentResponse.from(saved);
    }

    public ScheduleAssignmentResponse updateAssignment(
            Long organizationId,
            Long assignmentId,
            com.salaryai.salaryaiapi.attendance.dto.ScheduleAssignmentRequest request,
            String actor) {

        EmployeeScheduleAssignment assignment = assignmentRepository
                .findByIdAndOrganizationId(assignmentId, organizationId)
                .orElseThrow(com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException::new);
        support.validateVersion(request.version(), assignment.getVersion());
        AttendanceSchedule schedule = support.requireSchedule(organizationId, request.scheduleId());
        validateDateRange(request.effectiveTo(), request.effectiveFrom());
        validateNoOverlap(organizationId, assignment.getEmployee().getId(),
                request.effectiveFrom(), request.effectiveTo(), assignmentId);

        assignment.update(schedule, request.effectiveFrom(), request.effectiveTo());
        EmployeeScheduleAssignment saved =
                flush(() -> assignmentRepository.saveAndFlush(assignment));
        support.audit(organizationId, "SCHEDULE_ASSIGNMENT", saved.getId(),
                AuditAction.ASSIGNMENT_CREATED, actor, "Schedule assignment updated.");
        return ScheduleAssignmentResponse.from(saved);
    }

    public void deleteAssignment(
            Long organizationId, Long assignmentId, String actor, Long version) {

        EmployeeScheduleAssignment assignment = assignmentRepository
                .findByIdAndOrganizationId(assignmentId, organizationId)
                .orElseThrow(com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException::new);
        support.validateVersion(version, assignment.getVersion());
        assignmentRepository.delete(assignment);
        assignmentRepository.flush();
        support.audit(organizationId, "SCHEDULE_ASSIGNMENT", assignmentId,
                AuditAction.ASSIGNMENT_DELETED, actor, "Schedule assignment removed.");
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> listLeaveTypes(Long organizationId) {
        return leaveTypeRepository.findAllByOrganizationIdOrderByNameAsc(organizationId)
                .stream().map(LeaveTypeResponse::from).toList();
    }

    public LeaveTypeResponse createLeaveType(
            Long organizationId, String actor, LeaveTypeRequest request) {

        validateName(request.name());
        LeaveType leaveType = new LeaveType(
                support.organizationRef(organizationId), request.name(), request.paid(), request.active());
        LeaveType saved = flush(() -> leaveTypeRepository.saveAndFlush(leaveType));
        support.audit(organizationId, "LEAVE_TYPE", saved.getId(),
                AuditAction.LEAVE_TYPE_CREATED, actor, "Leave type " + saved.getName() + " created.");
        return LeaveTypeResponse.from(saved);
    }

    public LeaveTypeResponse updateLeaveType(
            Long organizationId, Long leaveTypeId, String actor, LeaveTypeRequest request) {

        LeaveType leaveType = support.requireLeaveType(organizationId, leaveTypeId);
        support.validateVersion(request.version(), leaveType.getVersion());
        validateName(request.name());
        if (leaveTypeRepository.existsByNameAndOrganizationIdAndIdNot(
                organizationId, request.name(), leaveTypeId)) {
            throw new AttendanceConflictException("A leave type with this name already exists.");
        }
        leaveType.update(request.paid(), request.active());
        LeaveType saved = flush(() -> leaveTypeRepository.saveAndFlush(leaveType));
        return LeaveTypeResponse.from(saved);
    }

    private void validateNoOverlap(
            Long organizationId, Long employeeId, LocalDate from, LocalDate to, Long excludeId) {

        LocalDate effectiveTo = to == null ? LocalDate.MAX : to;
        if (assignmentRepository.countOverlapping(
                organizationId, employeeId, from, effectiveTo, excludeId) > 0) {
            throw new AttendanceConflictException(
                    "Schedule assignments for this employee must not overlap.");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidAttendanceRequestException("Name is required.");
        }
    }

    private static void validateDateRange(LocalDate to, LocalDate from) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidAttendanceRequestException("End date must not be before start date.");
        }
    }

    private static <T> T flush(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OptimisticLockingFailureException exception) {
            throw new com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException();
        } catch (DataIntegrityViolationException exception) {
            throw new AttendanceConflictException(
                    "A conflicting attendance record already exists.");
        }
    }
}
