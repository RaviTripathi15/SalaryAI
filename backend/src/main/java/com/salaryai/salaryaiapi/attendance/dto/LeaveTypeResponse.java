package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.LeaveType;

public record LeaveTypeResponse(
        Long id, String name, boolean paid, boolean active, long version) {

    public static LeaveTypeResponse from(LeaveType leaveType) {
        return new LeaveTypeResponse(
                leaveType.getId(),
                leaveType.getName(),
                leaveType.isPaid(),
                leaveType.isActive(),
                leaveType.getVersion());
    }
}
