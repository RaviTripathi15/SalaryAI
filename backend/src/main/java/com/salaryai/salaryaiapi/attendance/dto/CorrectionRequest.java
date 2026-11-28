package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CorrectionRequest(
        @NotNull Long dailyAttendanceId,
        @NotBlank @Size(max = 500) String reason,
        @NotNull AttendanceStatus correctedStatus,
        Instant correctedCheckIn,
        Instant correctedCheckOut,
        @Min(0) long expectedVersion) {

    public CorrectionRequest {
        if (reason != null) {
            reason = reason.strip();
        }
    }
}
