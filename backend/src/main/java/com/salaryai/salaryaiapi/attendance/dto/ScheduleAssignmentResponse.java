package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.EmployeeScheduleAssignment;

import java.time.LocalDate;

public record ScheduleAssignmentResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long scheduleId,
        String scheduleName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {

    public static ScheduleAssignmentResponse from(EmployeeScheduleAssignment assignment) {
        return new ScheduleAssignmentResponse(
                assignment.getId(),
                assignment.getEmployee().getId(),
                assignment.getEmployee().getEmployeeCode(),
                assignment.getEmployee().getFirstName() + " " + assignment.getEmployee().getLastName(),
                assignment.getSchedule().getId(),
                assignment.getSchedule().getName(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveTo(),
                assignment.getVersion());
    }
}
