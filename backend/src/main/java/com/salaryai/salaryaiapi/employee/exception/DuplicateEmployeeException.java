package com.salaryai.salaryaiapi.employee.exception;

public final class DuplicateEmployeeException extends RuntimeException {

    public static final String MESSAGE =
            "An employee with these details already exists in this organization.";

    public DuplicateEmployeeException() {
        super(MESSAGE);
    }
}
