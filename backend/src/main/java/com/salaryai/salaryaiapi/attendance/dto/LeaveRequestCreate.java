package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveRequestCreate(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 500) String reason) {

    public LeaveRequestCreate {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
