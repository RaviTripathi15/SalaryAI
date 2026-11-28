package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceStatus;

import java.util.Map;

/** Derived exclusively from authoritative daily attendance records. */
public record MonthlySummaryResponse(
        int year,
        int month,
        Long employeeId,
        long totalDays,
        Map<AttendanceStatus, Long> statusCounts,
        long presentDays,
        long lateDays,
        long incompleteDays) {
}
