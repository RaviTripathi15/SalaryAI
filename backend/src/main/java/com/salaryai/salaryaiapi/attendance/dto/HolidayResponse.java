package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.Holiday;

import java.time.LocalDate;

public record HolidayResponse(
        Long id,
        String name,
        LocalDate holidayDate,
        String description,
        String calendarName,
        long version) {

    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getName(),
                holiday.getHolidayDate(),
                holiday.getDescription(),
                holiday.getCalendarName(),
                holiday.getVersion());
    }
}
