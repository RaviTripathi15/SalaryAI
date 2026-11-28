package com.salaryai.salaryaiapi.employee.dto;

import java.util.Locale;

public enum EmployeeSortField {

    ID("id"),
    EMPLOYEE_CODE("employeeCode"),
    FIRST_NAME("firstName"),
    LAST_NAME("lastName"),
    WORK_EMAIL("workEmail"),
    PHONE("phone"),
    DEPARTMENT("department"),
    DESIGNATION("designation"),
    JOINING_DATE("joiningDate"),
    EMPLOYMENT_STATUS("employmentStatus"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    DEACTIVATED_AT("deactivatedAt");

    private final String property;

    EmployeeSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }

    public static EmployeeSortField from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.strip();
        String normalized = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        for (EmployeeSortField field : values()) {
            if (field.name().equals(normalized)
                    || field.property().equalsIgnoreCase(trimmed)) {
                return field;
            }
        }
        return null;
    }
}
