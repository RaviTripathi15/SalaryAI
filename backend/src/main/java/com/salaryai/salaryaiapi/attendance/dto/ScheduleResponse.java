package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceSchedule;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        int breakMinutes,
        String workingDays,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        boolean overnight,
        long version) {

    public static ScheduleResponse from(AttendanceSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getName(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getBreakMinutes(),
                schedule.getWorkingDays(),
                schedule.getEffectiveFrom(),
                schedule.getEffectiveTo(),
                schedule.isActive(),
                schedule.isOvernight(),
                schedule.getVersion());
    }
}
