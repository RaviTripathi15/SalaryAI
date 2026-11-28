package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceRecordingService;
import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.dto.AttendanceEventResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.dto.PunchRequest;
import com.salaryai.salaryaiapi.attendance.dto.PunchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/organizations/attendance")
public class AttendancePunchController {

    private final AttendanceRecordingService recordingService;

    public AttendancePunchController(AttendanceRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/punches/check-in")
    public PunchResponse checkIn(
            @Valid @RequestBody PunchRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return recordingService.checkIn(
                organizationId, AttendanceRequestContext.actor(authentication), request);
    }

    @PostMapping("/punches/check-out")
    public PunchResponse checkOut(
            @Valid @RequestBody PunchRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return recordingService.checkOut(
                organizationId, AttendanceRequestContext.actor(authentication), request);
    }

    @GetMapping("/events")
    public PageResponse<AttendanceEventResponse> events(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return recordingService.events(organizationId, employeeId, fromDate, toDate, page, size);
    }
}
