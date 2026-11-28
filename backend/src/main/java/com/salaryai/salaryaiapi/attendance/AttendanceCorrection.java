package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.employee.Employee;
import com.salaryai.salaryaiapi.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only authorized correction. The original daily state is preserved
 * alongside the corrected state; nothing overwrites the source row silently.
 */
@Entity
@Table(
        name = "attendance_corrections",
        indexes = {
                @Index(name = "idx_corrections_org_daily",
                        columnList = "organization_id, daily_attendance_id"),
                @Index(name = "idx_corrections_org_time",
                        columnList = "organization_id, corrected_at")
        })
public class AttendanceCorrection extends AbstractImmutableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_corrections_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_attendance_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_corrections_daily_attendance"))
    private DailyAttendance dailyAttendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_corrections_employee"))
    private Employee employee;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "corrected_status", nullable = false, length = 20)
    private AttendanceStatus correctedStatus;

    @Column(name = "previous_check_in")
    private Instant previousCheckIn;

    @Column(name = "previous_check_out")
    private Instant previousCheckOut;

    @Column(name = "corrected_check_in")
    private Instant correctedCheckIn;

    @Column(name = "corrected_check_out")
    private Instant correctedCheckOut;

    @Column(name = "expected_version", nullable = false)
    private long expectedVersion;

    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @Column(name = "payroll_locked", nullable = false)
    private boolean payrollLocked;

    protected AttendanceCorrection() {
        // Required by JPA.
    }

    public AttendanceCorrection(
            Organization organization,
            DailyAttendance dailyAttendance,
            Employee employee,
            String reason,
            AttendanceStatus previousStatus,
            AttendanceStatus correctedStatus,
            Instant previousCheckIn,
            Instant previousCheckOut,
            Instant correctedCheckIn,
            Instant correctedCheckOut,
            long expectedVersion,
            String actor,
            boolean payrollLocked) {

        this.organization = organization;
        this.dailyAttendance = dailyAttendance;
        this.employee = employee;
        this.reason = reason;
        this.previousStatus = previousStatus;
        this.correctedStatus = correctedStatus;
        this.previousCheckIn = previousCheckIn;
        this.previousCheckOut = previousCheckOut;
        this.correctedCheckIn = correctedCheckIn;
        this.correctedCheckOut = correctedCheckOut;
        this.expectedVersion = expectedVersion;
        this.actor = actor;
        this.correctedAt = Instant.now();
        this.payrollLocked = payrollLocked;
    }

    public Organization getOrganization() {
        return organization;
    }

    public DailyAttendance getDailyAttendance() {
        return dailyAttendance;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getReason() {
        return reason;
    }

    public AttendanceStatus getPreviousStatus() {
        return previousStatus;
    }

    public AttendanceStatus getCorrectedStatus() {
        return correctedStatus;
    }

    public Instant getPreviousCheckIn() {
        return previousCheckIn;
    }

    public Instant getPreviousCheckOut() {
        return previousCheckOut;
    }

    public Instant getCorrectedCheckIn() {
        return correctedCheckIn;
    }

    public Instant getCorrectedCheckOut() {
        return correctedCheckOut;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public String getActor() {
        return actor;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }

    public boolean isPayrollLocked() {
        return payrollLocked;
    }
}
