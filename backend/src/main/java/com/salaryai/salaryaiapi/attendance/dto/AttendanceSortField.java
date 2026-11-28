package com.salaryai.salaryaiapi.attendance.dto;

import java.util.Locale;

/** Explicit allowlist of sortable daily-attendance properties. */
public enum AttendanceSortField {
    WORK_DATE("workDate"),
    STATUS("status"),
    EMPLOYEE_CODE("employee.employeeCode"),
    FIRST_NAME("employee.firstName"),
    LAST_NAME("employee.lastName"),
    FIRST_CHECK_IN("firstCheckIn"),
    LAST_CHECK_OUT("lastCheckOut"),
    WORKED_MINUTES("workedMinutes"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String property;

    AttendanceSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }

    public static AttendanceSortField from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        String normalized = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        for (AttendanceSortField field : values()) {
            if (field.name().equals(normalized)
                    || field.property().equalsIgnoreCase(trimmed)) {
                return field;
            }
        }
        return null;
    }
}
