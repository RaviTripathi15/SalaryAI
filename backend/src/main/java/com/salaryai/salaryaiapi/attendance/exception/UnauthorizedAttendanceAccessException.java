package com.salaryai.salaryaiapi.attendance.exception;

public final class UnauthorizedAttendanceAccessException extends RuntimeException {

    public static final String MESSAGE =
            "This account is not authorized for the organization workspace.";

    public UnauthorizedAttendanceAccessException() {
        super(MESSAGE);
    }
}
