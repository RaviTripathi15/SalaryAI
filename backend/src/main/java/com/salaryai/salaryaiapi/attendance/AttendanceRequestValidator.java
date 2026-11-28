package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.AttendanceSortField;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

/** Shared request-bound validation. All values are bounded and allowlisted. */
public final class AttendanceRequestValidator {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_NUMBER = 1_000_000;

    private AttendanceRequestValidator() {
    }

    public static void page(int page, int size) {
        if (page < 0 || page > MAX_PAGE_NUMBER) {
            throw new InvalidAttendanceRequestException("Invalid page index.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidAttendanceRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    public static Sort.Direction direction(String direction) {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.ASC;
        }
        String normalized = direction.strip().toUpperCase(java.util.Locale.ROOT);
        if ("ASC".equals(normalized)) {
            return Sort.Direction.ASC;
        }
        if ("DESC".equals(normalized)) {
            return Sort.Direction.DESC;
        }
        throw new InvalidAttendanceRequestException("Invalid sort direction.");
    }

    public static AttendanceSortField sortField(String sortBy) {
        AttendanceSortField field = AttendanceSortField.from(sortBy);
        if (field == null) {
            throw new InvalidAttendanceRequestException("Invalid sort field.");
        }
        return field;
    }

    public static void dateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new InvalidAttendanceRequestException(
                    "End date must not be before start date.");
        }
    }

    public static void yearMonth(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new InvalidAttendanceRequestException("Invalid year or month.");
        }
    }
}
