package com.salaryai.salaryaiapi.employee.exception;

public final class StaleEmployeeVersionException extends RuntimeException {

    public static final String MESSAGE =
            "The employee record changed. Reload it and try again.";

    public StaleEmployeeVersionException() {
        super(MESSAGE);
    }
}
