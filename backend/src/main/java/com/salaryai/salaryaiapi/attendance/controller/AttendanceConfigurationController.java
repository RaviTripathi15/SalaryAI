package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceConfigurationService;
import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.dto.LeaveTypeRequest;
import com.salaryai.salaryaiapi.attendance.dto.LeaveTypeResponse;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleAssignmentRequest;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleAssignmentResponse;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleRequest;
import com.salaryai.salaryaiapi.attendance.dto.ScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/attendance")
public class AttendanceConfigurationController {

    private static final String SCHEDULE_PATH = "/api/v1/organizations/attendance/schedules";
    private static final String ASSIGNMENT_PATH = "/api/v1/organizations/attendance/assignments";
    private static final String LEAVE_TYPE_PATH = "/api/v1/organizations/attendance/leave-types";

    private final AttendanceConfigurationService configurationService;

    public AttendanceConfigurationController(AttendanceConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/schedules")
    public List<ScheduleResponse> listSchedules(Authentication authentication) {
        return configurationService.listSchedules(
                AttendanceRequestContext.organizationId(authentication));
    }

    @PostMapping("/schedules")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @Valid @RequestBody ScheduleRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        ScheduleResponse created = configurationService.createSchedule(
                organizationId, AttendanceRequestContext.actor(authentication), request);
        return ResponseEntity.created(URI.create(SCHEDULE_PATH + "/" + created.id())).body(created);
    }

    @PutMapping("/schedules/{id}")
    public ScheduleResponse updateSchedule(
            @PathVariable("id") Long id,
            @Valid @RequestBody ScheduleRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.updateSchedule(
                organizationId, id, AttendanceRequestContext.actor(authentication), request);
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable("id") Long id,
            @RequestParam(name = "version", required = false) Long version,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        configurationService.deleteSchedule(
                organizationId, id, AttendanceRequestContext.actor(authentication), version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments")
    public List<ScheduleAssignmentResponse> listAssignments(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.listAssignments(organizationId, employeeId);
    }

    @PostMapping("/assignments")
    public ResponseEntity<ScheduleAssignmentResponse> createAssignment(
            @Valid @RequestBody ScheduleAssignmentRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        ScheduleAssignmentResponse created = configurationService.createAssignment(
                organizationId, request, AttendanceRequestContext.actor(authentication));
        return ResponseEntity.created(URI.create(ASSIGNMENT_PATH + "/" + created.id())).body(created);
    }

    @PutMapping("/assignments/{id}")
    public ScheduleAssignmentResponse updateAssignment(
            @PathVariable("id") Long id,
            @Valid @RequestBody ScheduleAssignmentRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.updateAssignment(
                organizationId, id, request, AttendanceRequestContext.actor(authentication));
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable("id") Long id,
            @RequestParam(name = "version", required = false) Long version,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        configurationService.deleteAssignment(
                organizationId, id, AttendanceRequestContext.actor(authentication), version);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/leave-types")
    public List<LeaveTypeResponse> listLeaveTypes(Authentication authentication) {
        return configurationService.listLeaveTypes(
                AttendanceRequestContext.organizationId(authentication));
    }

    @PostMapping("/leave-types")
    public ResponseEntity<LeaveTypeResponse> createLeaveType(
            @Valid @RequestBody LeaveTypeRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        LeaveTypeResponse created = configurationService.createLeaveType(
                organizationId, AttendanceRequestContext.actor(authentication), request);
        return ResponseEntity.created(URI.create(LEAVE_TYPE_PATH + "/" + created.id())).body(created);
    }

    @PutMapping("/leave-types/{id}")
    public LeaveTypeResponse updateLeaveType(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeaveTypeRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.updateLeaveType(
                organizationId, id, AttendanceRequestContext.actor(authentication), request);
    }
}
