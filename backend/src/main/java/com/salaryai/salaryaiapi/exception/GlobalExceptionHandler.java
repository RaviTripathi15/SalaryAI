package com.salaryai.salaryaiapi.exception;

import com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException;
import com.salaryai.salaryaiapi.attendance.exception.UnauthorizedAttendanceAccessException;
import com.salaryai.salaryaiapi.employee.exception.DuplicateEmployeeException;
import com.salaryai.salaryaiapi.employee.exception.EmployeeNotFoundException;
import com.salaryai.salaryaiapi.employee.exception.InvalidEmployeeRequestException;
import com.salaryai.salaryaiapi.employee.exception.StaleEmployeeVersionException;
import com.salaryai.salaryaiapi.employee.exception.UnauthorizedEmployeeAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String UNEXPECTED_ERROR = "An unexpected error occurred.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid registration details.");

        return ResponseEntity
                .badRequest()
                .body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(
            IllegalStateException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEmployeeNotFound(
            EmployeeNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", EmployeeNotFoundException.MESSAGE));
    }

    @ExceptionHandler(DuplicateEmployeeException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmployee(
            DuplicateEmployeeException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", DuplicateEmployeeException.MESSAGE));
    }

    @ExceptionHandler(StaleEmployeeVersionException.class)
    public ResponseEntity<Map<String, String>> handleStaleEmployeeVersion(
            StaleEmployeeVersionException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", StaleEmployeeVersionException.MESSAGE));
    }

    @ExceptionHandler(InvalidEmployeeRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidEmployeeRequest(
            InvalidEmployeeRequestException exception) {

        return ResponseEntity
                .badRequest()
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(UnauthorizedEmployeeAccessException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedEmployeeAccess(
            UnauthorizedEmployeeAccessException exception) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", UnauthorizedEmployeeAccessException.MESSAGE));
    }

    @ExceptionHandler(AttendanceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleAttendanceNotFound(
            AttendanceNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", AttendanceNotFoundException.MESSAGE));
    }

    @ExceptionHandler(InvalidAttendanceRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAttendanceRequest(
            InvalidAttendanceRequestException exception) {

        return ResponseEntity
                .badRequest()
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(AttendanceConflictException.class)
    public ResponseEntity<Map<String, String>> handleAttendanceConflict(
            AttendanceConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(StaleAttendanceVersionException.class)
    public ResponseEntity<Map<String, String>> handleStaleAttendanceVersion(
            StaleAttendanceVersionException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", StaleAttendanceVersionException.MESSAGE));
    }

    @ExceptionHandler(UnauthorizedAttendanceAccessException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedAttendanceAccess(
            UnauthorizedAttendanceAccessException exception) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", UnauthorizedAttendanceAccessException.MESSAGE));
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> handleMalformedRequest() {
        return ResponseEntity
                .badRequest()
                .body(Map.of("error", "Invalid request."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception) {

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(new HttpHeaders(exception.getHeaders()))
                .body(Map.of("error", "Method not allowed."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedError(
            Exception exception) throws Exception {

        // Let Spring MVC's ErrorResponse resolvers and Spring Security's
        // exception translation filter keep ownership of framework/security
        // exceptions instead of converting them to a generic 500.
        if (exception instanceof ErrorResponse
                || exception instanceof AccessDeniedException
                || exception instanceof AuthenticationException) {
            throw exception;
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", UNEXPECTED_ERROR));
    }
}
