package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull LocalDate holidayDate,
        @Size(max = 500) String description,
        @Size(max = 100) String calendarName,
        Long version) {

    public HolidayRequest {
        if (name != null) {
            name = name.strip();
        }
        if (description != null && description.isBlank()) {
            description = null;
        }
        if (calendarName != null && calendarName.isBlank()) {
            calendarName = null;
        }
    }
}
