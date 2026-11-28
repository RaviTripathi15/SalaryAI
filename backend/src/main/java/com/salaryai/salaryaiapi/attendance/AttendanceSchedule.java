package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "attendance_schedules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_schedules_org_name",
                columnNames = {"organization_id", "name"}),
        indexes = @Index(
                name = "idx_attendance_schedules_org_active",
                columnList = "organization_id, active, effective_from"))
public class AttendanceSchedule extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_schedules_organization"))
    private Organization organization;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes;

    @Column(name = "working_days", nullable = false, length = 64)
    private String workingDays;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "overnight", nullable = false)
    private boolean overnight;

    protected AttendanceSchedule() {
        // Required by JPA.
    }

    public AttendanceSchedule(
            Organization organization,
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            String workingDays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            boolean overnight) {

        this.organization = organization;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.workingDays = workingDays;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.active = active;
        this.overnight = overnight;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isOvernight() {
        return overnight;
    }

    public void update(
            String name,
            LocalTime startTime,
            LocalTime endTime,
            int breakMinutes,
            String workingDays,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            boolean overnight) {

        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.workingDays = workingDays;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.active = active;
        this.overnight = overnight;
    }
}
