package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.LeaveService;
import com.salaryai.salaryaiapi.attendance.LeaveStatus;
import com.salaryai.salaryaiapi.attendance.dto.LeaveDecisionRequest;
import com.salaryai.salaryaiapi.attendance.dto.LeaveRequestCreate;
import com.salaryai.salaryaiapi.attendance.dto.LeaveRequestResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/organizations/attendance/leave-requests")
public class AttendanceLeaveController {

    private static final String PATH =
            "/api/v1/organizations/attendance/leave-requests";

    private final LeaveService leaveService;

    public AttendanceLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> create(
            @Valid @RequestBody LeaveRequestCreate request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        LeaveRequestResponse created = leaveService.create(
                organizationId, AttendanceRequestContext.actor(authentication), request);
        return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<LeaveRequestResponse> list(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "status", required = false) LeaveStatus status,
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return leaveService.list(
                organizationId, employeeId, status, fromDate, toDate, page, size);
    }

    @GetMapping("/{id}")
    public LeaveRequestResponse get(
            @PathVariable("id") Long id,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return leaveService.get(organizationId, id);
    }

    @PostMapping("/{id}/approve")
    public LeaveRequestResponse approve(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeaveDecisionRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return leaveService.approve(
                organizationId, id, AttendanceRequestContext.actor(authentication),
                request.version());
    }

    @PostMapping("/{id}/reject")
    public LeaveRequestResponse reject(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeaveDecisionRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return leaveService.reject(
                organizationId, id, AttendanceRequestContext.actor(authentication),
                request.version());
    }

    @PostMapping("/{id}/cancel")
    public LeaveRequestResponse cancel(
            @PathVariable("id") Long id,
            @Valid @RequestBody LeaveDecisionRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return leaveService.cancel(
                organizationId, id, AttendanceRequestContext.actor(authentication),
                request.version());
    }
}
