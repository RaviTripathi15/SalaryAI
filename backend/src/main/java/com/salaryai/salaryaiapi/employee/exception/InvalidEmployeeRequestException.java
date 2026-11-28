package com.salaryai.salaryaiapi.employee.exception;

public final class InvalidEmployeeRequestException extends RuntimeException {

    public InvalidEmployeeRequestException(String message) {
        super(message);
    }
}
