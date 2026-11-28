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

/** Immutable record of a check-in or check-out action. Never updated or deleted. */
@Entity
@Table(
        name = "attendance_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_events_idempotency",
                columnNames = {"organization_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_attendance_events_org_employee_date",
                        columnList = "organization_id, employee_id, work_date"),
                @Index(name = "idx_attendance_events_org_employee_time",
                        columnList = "organization_id, employee_id, occurred_at")
        })
public class AttendanceEvent extends AbstractImmutableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_events_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attendance_events_employee"))
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AttendanceEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "time_zone_id", nullable = false, length = 64)
    private String timeZoneId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AttendanceEvent() {
        // Required by JPA.
    }

    public AttendanceEvent(
            Organization organization,
            Employee employee,
            AttendanceEventType eventType,
            Instant occurredAt,
            LocalDate workDate,
            String timeZoneId,
            String idempotencyKey) {

        this.organization = organization;
        this.employee = employee;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.workDate = workDate;
        this.timeZoneId = timeZoneId;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public Organization getOrganization() {
        return organization;
    }

    public Employee getEmployee() {
        return employee;
    }

    public AttendanceEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
