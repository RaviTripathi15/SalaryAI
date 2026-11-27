package com.salaryai.salaryaiapi.employee;

/**
 * Employment lifecycle status.
 *
 * <p>Persisted as a string via {@link jakarta.persistence.EnumType#STRING}
 * on {@link Employee#getEmploymentStatus()}. New employees are ACTIVE.</p>
 */
public enum EmploymentStatus {
    ACTIVE,
    INACTIVE
}
