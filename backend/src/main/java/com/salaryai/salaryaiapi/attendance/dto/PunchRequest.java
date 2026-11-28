package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Check-in/check-out request. Client-supplied organization identity is never accepted. */
public record PunchRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        Instant occurredAt) {

    public PunchRequest {
        if (idempotencyKey != null) {
            idempotencyKey = idempotencyKey.strip();
        }
    }
}
