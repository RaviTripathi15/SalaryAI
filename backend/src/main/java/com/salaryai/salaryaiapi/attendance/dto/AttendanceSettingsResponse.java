package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceSettings;
import com.salaryai.salaryaiapi.attendance.CalculationPolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceSettingsResponse(
        Long id,
        String timeZoneId,
        int gracePeriodMinutes,
        CalculationPolicy calculationPolicy,
        LocalTime defaultStartTime,
        LocalTime defaultEndTime,
        int defaultBreakMinutes,
        String defaultWorkingDays,
        LocalDate payrollLockThrough,
        long version) {

    public static AttendanceSettingsResponse from(AttendanceSettings settings) {
        return new AttendanceSettingsResponse(
                settings.getId(),
                settings.getTimeZoneId(),
                settings.getGracePeriodMinutes(),
                settings.getCalculationPolicy(),
                settings.getDefaultStartTime(),
                settings.getDefaultEndTime(),
                settings.getDefaultBreakMinutes(),
                settings.getDefaultWorkingDays(),
                settings.getPayrollLockThrough(),
                settings.getVersion());
    }
}
