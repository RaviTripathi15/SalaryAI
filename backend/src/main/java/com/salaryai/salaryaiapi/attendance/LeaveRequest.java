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
import java.time.LocalDate;

@Entity
@Table(
        name = "leave_requests",
        indexes = {
                @Index(name = "idx_leave_requests_org_status_start",
                        columnList = "organization_id, status, start_date"),
                @Index(name = "idx_leave_requests_org_employee_start",
                        columnList = "organization_id, employee_id, start_date")
        })
public class LeaveRequest extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_requests_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_requests_employee"))
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_requests_leave_type"))
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveStatus status;

    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected LeaveRequest() {
        // Required by JPA.
    }

    public LeaveRequest(
            Organization organization,
            Employee employee,
            LeaveType leaveType,
            LocalDate startDate,
            LocalDate endDate,
            String reason) {

        this.organization = organization;
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = LeaveStatus.PENDING;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void decide(LeaveStatus status, String actor) {
        this.status = status;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
    }
}
