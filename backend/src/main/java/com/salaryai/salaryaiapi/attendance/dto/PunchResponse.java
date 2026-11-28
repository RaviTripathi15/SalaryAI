package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceEventType;
import com.salaryai.salaryaiapi.attendance.DailyAttendance;

import java.time.Instant;
import java.time.LocalDate;

public record PunchResponse(
        Long eventId,
        Long dailyAttendanceId,
        AttendanceEventType eventType,
        Long employeeId,
        LocalDate workDate,
        Instant occurredAt,
        String timeZoneId,
        boolean duplicateRequest,
        DailyAttendanceResponse dailyAttendance) {
}
