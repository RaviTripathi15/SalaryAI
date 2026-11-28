package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_settings")
public class AttendanceSettings extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_settings_organization"))
    private Organization organization;

    @Column(name = "time_zone_id", nullable = false, length = 64)
    private String timeZoneId;

    @Column(name = "grace_period_minutes", nullable = false)
    private int gracePeriodMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_policy", nullable = false, length = 30)
    private CalculationPolicy calculationPolicy;

    @Column(name = "default_start_time", nullable = false)
    private LocalTime defaultStartTime;

    @Column(name = "default_end_time", nullable = false)
    private LocalTime defaultEndTime;

    @Column(name = "default_break_minutes", nullable = false)
    private int defaultBreakMinutes;

    @Column(name = "default_working_days", nullable = false, length = 64)
    private String defaultWorkingDays;

    @Column(name = "payroll_lock_through")
    private LocalDate payrollLockThrough;

    protected AttendanceSettings() {
        // Required by JPA.
    }

    public AttendanceSettings(
            Organization organization,
            String timeZoneId,
            int gracePeriodMinutes,
            CalculationPolicy calculationPolicy,
            LocalTime defaultStartTime,
            LocalTime defaultEndTime,
            int defaultBreakMinutes,
            String defaultWorkingDays) {

        this.organization = organization;
        this.timeZoneId = timeZoneId;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.calculationPolicy = calculationPolicy;
        this.defaultStartTime = defaultStartTime;
        this.defaultEndTime = defaultEndTime;
        this.defaultBreakMinutes = defaultBreakMinutes;
        this.defaultWorkingDays = defaultWorkingDays;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public int getGracePeriodMinutes() {
        return gracePeriodMinutes;
    }

    public CalculationPolicy getCalculationPolicy() {
        return calculationPolicy;
    }

    public LocalTime getDefaultStartTime() {
        return defaultStartTime;
    }

    public LocalTime getDefaultEndTime() {
        return defaultEndTime;
    }

    public int getDefaultBreakMinutes() {
        return defaultBreakMinutes;
    }

    public String getDefaultWorkingDays() {
        return defaultWorkingDays;
    }

    public LocalDate getPayrollLockThrough() {
        return payrollLockThrough;
    }

    public void update(
            String timeZoneId,
            int gracePeriodMinutes,
            CalculationPolicy calculationPolicy,
            LocalTime defaultStartTime,
            LocalTime defaultEndTime,
            int defaultBreakMinutes,
            String defaultWorkingDays,
            LocalDate payrollLockThrough) {

        this.timeZoneId = timeZoneId;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.calculationPolicy = calculationPolicy;
        this.defaultStartTime = defaultStartTime;
        this.defaultEndTime = defaultEndTime;
        this.defaultBreakMinutes = defaultBreakMinutes;
        this.defaultWorkingDays = defaultWorkingDays;
        this.payrollLockThrough = payrollLockThrough;
    }
}
