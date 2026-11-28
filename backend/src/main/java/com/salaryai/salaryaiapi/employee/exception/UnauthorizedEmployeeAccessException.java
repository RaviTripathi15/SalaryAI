package com.salaryai.salaryaiapi.employee.exception;

public final class UnauthorizedEmployeeAccessException extends RuntimeException {

    public static final String MESSAGE =
            "This account is not authorized for the organization workspace.";

    public UnauthorizedEmployeeAccessException() {
        super(MESSAGE);
    }
}
