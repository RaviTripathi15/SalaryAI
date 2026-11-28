package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeaveTypeRequest(
        @NotBlank @Size(max = 100) String name,
        boolean paid,
        boolean active,
        Long version) {

    public LeaveTypeRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
