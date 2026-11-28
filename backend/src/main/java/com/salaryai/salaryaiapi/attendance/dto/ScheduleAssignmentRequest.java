package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScheduleAssignmentRequest(
        @NotNull Long employeeId,
        @NotNull Long scheduleId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long version) {
}
