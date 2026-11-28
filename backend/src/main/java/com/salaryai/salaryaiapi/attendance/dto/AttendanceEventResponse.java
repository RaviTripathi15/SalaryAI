package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceEvent;
import com.salaryai.salaryaiapi.attendance.AttendanceEventType;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceEventResponse(
        Long id,
        Long employeeId,
        AttendanceEventType eventType,
        Instant occurredAt,
        LocalDate workDate,
        String timeZoneId,
        Instant createdAt) {

    public static AttendanceEventResponse from(AttendanceEvent event) {
        return new AttendanceEventResponse(
                event.getId(),
                event.getEmployee().getId(),
                event.getEventType(),
                event.getOccurredAt(),
                event.getWorkDate(),
                event.getTimeZoneId(),
                event.getCreatedAt());
    }
}
