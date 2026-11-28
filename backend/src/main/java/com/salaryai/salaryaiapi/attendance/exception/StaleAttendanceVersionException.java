package com.salaryai.salaryaiapi.attendance.exception;

public final class StaleAttendanceVersionException extends RuntimeException {

    public static final String MESSAGE = "The attendance record changed. Reload it and try again.";

    public StaleAttendanceVersionException() {
        super(MESSAGE);
    }
}
