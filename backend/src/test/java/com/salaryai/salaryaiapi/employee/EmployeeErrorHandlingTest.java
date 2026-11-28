package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.employee.exception.DuplicateEmployeeException;
import com.salaryai.salaryaiapi.employee.exception.EmployeeNotFoundException;
import com.salaryai.salaryaiapi.employee.exception.InvalidEmployeeRequestException;
import com.salaryai.salaryaiapi.employee.exception.StaleEmployeeVersionException;
import com.salaryai.salaryaiapi.employee.exception.UnauthorizedEmployeeAccessException;
import com.salaryai.salaryaiapi.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeErrorHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedErrorReturnsGenericMessageWithoutInternalDetails() throws Exception {
        ResponseEntity<Map<String, String>> response = handler.handleUnexpectedError(
                new RuntimeException("SELECT * FROM employees WHERE password = 'secret'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred.");
        assertThat(response.getBody().get("error"))
                .doesNotContain("SELECT", "password", "secret", "employees");
    }

    @Test
    void frameworkAndSecurityExceptionsAreRethrownToTheirOwnResolvers() throws Exception {
        ResponseStatusException statusException =
                new ResponseStatusException(HttpStatus.NOT_FOUND);
        AccessDeniedException accessDeniedException = new AccessDeniedException("denied");
        BadCredentialsException authenticationException = new BadCredentialsException("bad");

        assertThatThrownBy(() -> handler.handleUnexpectedError(statusException))
                .isSameAs(statusException);
        assertThatThrownBy(() -> handler.handleUnexpectedError(accessDeniedException))
                .isSameAs(accessDeniedException);
        assertThatThrownBy(() -> handler.handleUnexpectedError(authenticationException))
                .isSameAs(authenticationException);
    }

    @Test
    void methodNotAllowedResponsePreservesAllowHeader() {
        ResponseEntity<Map<String, String>> response = handler.handleUnsupportedMethod(
                new HttpRequestMethodNotSupportedException(
                        "DELETE", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void malformedRequestReturnsBadRequestWithoutUrl() {
        ResponseEntity<Map<String, String>> response = handler.handleMalformedRequest();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid request.");
    }

    @Test
    void employeeErrorsUseStableStatusAndMessages() {
        assertThat(handler.handleEmployeeNotFound(new EmployeeNotFoundException()))
                .satisfies(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).containsEntry(
                            "error", "Employee not found.");
                });
        assertThat(handler.handleDuplicateEmployee(new DuplicateEmployeeException()))
                .satisfies(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(response.getBody()).containsEntry(
                            "error", DuplicateEmployeeException.MESSAGE);
                });
        assertThat(handler.handleStaleEmployeeVersion(new StaleEmployeeVersionException()))
                .satisfies(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(response.getBody()).containsEntry(
                            "error", StaleEmployeeVersionException.MESSAGE);
                });
        assertThat(handler.handleInvalidEmployeeRequest(
                new InvalidEmployeeRequestException("Invalid sort field.")))
                .satisfies(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).containsEntry("error", "Invalid sort field.");
                });
        assertThat(handler.handleUnauthorizedEmployeeAccess(
                new UnauthorizedEmployeeAccessException()))
                .satisfies(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(response.getBody()).containsEntry(
                            "error", UnauthorizedEmployeeAccessException.MESSAGE);
                });
    }
}
