package com.salaryai.salaryaiapi.employee.exception;

public final class EmployeeNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Employee not found.";

    public EmployeeNotFoundException() {
        super(MESSAGE);
    }
}
