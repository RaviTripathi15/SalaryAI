package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceStatus;
import com.salaryai.salaryaiapi.attendance.DailyAttendance;

import java.time.Instant;
import java.time.LocalDate;

public record DailyAttendanceResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate workDate,
        AttendanceStatus status,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant firstCheckIn,
        Instant lastCheckOut,
        int workedMinutes,
        int lateMinutes,
        int breakMinutes,
        long version) {

    public static DailyAttendanceResponse from(DailyAttendance record) {
        return new DailyAttendanceResponse(
                record.getId(),
                record.getEmployee().getId(),
                record.getEmployee().getEmployeeCode(),
                record.getEmployee().getFirstName() + " " + record.getEmployee().getLastName(),
                record.getWorkDate(),
                record.getStatus(),
                record.getScheduledStart(),
                record.getScheduledEnd(),
                record.getFirstCheckIn(),
                record.getLastCheckOut(),
                record.getWorkedMinutes(),
                record.getLateMinutes(),
                record.getBreakMinutes(),
                record.getVersion());
    }
}
