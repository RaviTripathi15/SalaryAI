package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(480) int breakMinutes,
        @NotBlank @Size(max = 64) String workingDays,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        boolean overnight,
        Long version) {

    public ScheduleRequest {
        if (name != null) {
            name = name.strip();
        }
        if (workingDays != null) {
            workingDays = workingDays.strip().toUpperCase(java.util.Locale.ROOT);
        }
    }
}
