package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceQueryService;
import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.AttendanceStatus;
import com.salaryai.salaryaiapi.attendance.dto.AuditRecordResponse;
import com.salaryai.salaryaiapi.attendance.dto.DailyAttendanceResponse;
import com.salaryai.salaryaiapi.attendance.dto.MonthlySummaryResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.dto.StatusCountResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/attendance")
public class AttendanceQueryController {

    private final AttendanceQueryService queryService;

    public AttendanceQueryController(AttendanceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/daily")
    public PageResponse<DailyAttendanceResponse> listDaily(
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "status", required = false) AttendanceStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "workDate") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.listDaily(
                organizationId, employeeId, fromDate, toDate, status,
                page, size, sortBy, sortDirection);
    }

    @GetMapping("/daily/overview")
    public PageResponse<DailyAttendanceResponse> overview(
            @RequestParam(name = "date") LocalDate date,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.dailyOverview(organizationId, date, page, size);
    }

    @GetMapping("/daily/status-counts")
    public List<StatusCountResponse> statusCounts(
            @RequestParam(name = "date") LocalDate date,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.statusCounts(organizationId, date);
    }

    @GetMapping("/daily/{employeeId}/{date}")
    public DailyAttendanceResponse dailyDetail(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("date") LocalDate date,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.dailyDetail(organizationId, employeeId, date);
    }

    @GetMapping("/monthly")
    public MonthlySummaryResponse monthlySummary(
            @RequestParam(name = "year") int year,
            @RequestParam(name = "month") int month,
            @RequestParam(name = "employeeId", required = false) Long employeeId,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.monthlySummary(organizationId, employeeId, year, month);
    }

    @GetMapping("/audit")
    public PageResponse<AuditRecordResponse> audit(
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "entityId", required = false) Long entityId,
            @RequestParam(name = "fromTime", required = false) Instant fromTime,
            @RequestParam(name = "toTime", required = false) Instant toTime,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return queryService.audit(
                organizationId, entityType, entityId, fromTime, toTime, page, size);
    }
}
