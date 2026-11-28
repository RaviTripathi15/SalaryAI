package com.salaryai.salaryaiapi.attendance.exception;

/** Generic not-found response used for missing and cross-tenant attendance resources. */
public final class AttendanceNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Attendance record not found.";

    public AttendanceNotFoundException() {
        super(MESSAGE);
    }
}
