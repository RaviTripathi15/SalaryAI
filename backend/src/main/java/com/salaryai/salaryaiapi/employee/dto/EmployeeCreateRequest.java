package com.salaryai.salaryaiapi.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeeCreateRequest(

        @NotBlank(message = "Employee code is required")
        @Size(max = 50, message = "Employee code must not exceed 50 characters")
        String employeeCode,

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "Work email is required")
        @Email(message = "Enter a valid work email address")
        @Size(max = 255, message = "Work email must not exceed 255 characters")
        String workEmail,

        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phone,

        @Size(max = 100, message = "Department must not exceed 100 characters")
        String department,

        @Size(max = 100, message = "Designation must not exceed 100 characters")
        String designation,

        @NotNull(message = "Joining date is required")
        LocalDate joiningDate

) {
    public EmployeeCreateRequest {
        employeeCode = trim(employeeCode);
        firstName = trim(firstName);
        lastName = trim(lastName);
        workEmail = trim(workEmail);
        phone = trim(phone);
        department = trim(department);
        designation = trim(designation);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
