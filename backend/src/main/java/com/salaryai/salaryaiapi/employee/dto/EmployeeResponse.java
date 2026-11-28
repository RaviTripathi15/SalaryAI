package com.salaryai.salaryaiapi.employee.dto;

import com.salaryai.salaryaiapi.employee.Employee;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(

        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String workEmail,
        String phone,
        String department,
        String designation,
        LocalDate joiningDate,
        String employmentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deactivatedAt,
        Long version

) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getWorkEmail(),
                employee.getPhone(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getJoiningDate(),
                employee.getEmploymentStatus().name(),
                employee.getCreatedAt(),
                employee.getUpdatedAt(),
                employee.getDeactivatedAt(),
                employee.getVersion()
        );
    }
}
