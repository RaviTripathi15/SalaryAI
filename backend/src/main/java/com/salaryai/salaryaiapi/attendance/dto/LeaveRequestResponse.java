package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.LeaveRequest;
import com.salaryai.salaryaiapi.attendance.LeaveStatus;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long leaveTypeId,
        String leaveTypeName,
        boolean paid,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LeaveStatus status,
        String decidedBy,
        Instant decidedAt,
        long version) {

    public static LeaveRequestResponse from(LeaveRequest request) {
        return new LeaveRequestResponse(
                request.getId(),
                request.getEmployee().getId(),
                request.getEmployee().getEmployeeCode(),
                request.getEmployee().getFirstName() + " " + request.getEmployee().getLastName(),
                request.getLeaveType().getId(),
                request.getLeaveType().getName(),
                request.getLeaveType().isPaid(),
                request.getStartDate(),
                request.getEndDate(),
                request.getReason(),
                request.getStatus(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getVersion());
    }
}
