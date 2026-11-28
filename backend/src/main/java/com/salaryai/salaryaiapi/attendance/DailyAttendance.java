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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/** Authoritative calculated daily attendance state. */
@Entity
@Table(
        name = "daily_attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_attendance_org_employee_date",
                columnNames = {"organization_id", "employee_id", "work_date"}),
        indexes = @Index(
                name = "idx_daily_attendance_org_date_status",
                columnList = "organization_id, work_date, status"))
public class DailyAttendance extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_attendance_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_attendance_employee"))
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "first_check_in")
    private Instant firstCheckIn;

    @Column(name = "last_check_out")
    private Instant lastCheckOut;

    @Column(name = "worked_minutes", nullable = false)
    private int workedMinutes;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    protected DailyAttendance() {
        // Required by JPA.
    }

    public DailyAttendance(
            Organization organization,
            Employee employee,
            LocalDate workDate,
            AttendanceStatus status) {

        this.organization = organization;
        this.employee = employee;
        this.workDate = workDate;
        this.status = status;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public Instant getFirstCheckIn() {
        return firstCheckIn;
    }

    public Instant getLastCheckOut() {
        return lastCheckOut;
    }

    public int getWorkedMinutes() {
        return workedMinutes;
    }

    public int getLateMinutes() {
        return lateMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void applyCalculation(
            AttendanceStatus status,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant firstCheckIn,
            Instant lastCheckOut,
            int workedMinutes,
            int lateMinutes,
            int breakMinutes) {

        this.status = status;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.firstCheckIn = firstCheckIn;
        this.lastCheckOut = lastCheckOut;
        this.workedMinutes = workedMinutes;
        this.lateMinutes = lateMinutes;
        this.breakMinutes = breakMinutes;
    }
}
