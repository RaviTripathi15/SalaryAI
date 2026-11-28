package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.CalculationPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceSettingsRequest(
        @NotBlank @Size(max = 64) String timeZoneId,
        @Min(0) @Max(240) int gracePeriodMinutes,
        @NotNull CalculationPolicy calculationPolicy,
        @NotNull LocalTime defaultStartTime,
        @NotNull LocalTime defaultEndTime,
        @Min(0) @Max(480) int defaultBreakMinutes,
        @NotBlank @Size(max = 64) String defaultWorkingDays,
        LocalDate payrollLockThrough,
        @Min(0) long version) {

    public AttendanceSettingsRequest {
        if (timeZoneId != null) {
            timeZoneId = timeZoneId.strip();
        }
        if (defaultWorkingDays != null) {
            defaultWorkingDays = defaultWorkingDays.strip().toUpperCase(java.util.Locale.ROOT);
        }
    }
}
