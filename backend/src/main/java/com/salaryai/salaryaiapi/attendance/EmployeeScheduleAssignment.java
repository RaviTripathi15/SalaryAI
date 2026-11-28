package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.employee.Employee;
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

@Entity
@Table(
        name = "employee_schedule_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_assignments_org_employee_from",
                columnNames = {"organization_id", "employee_id", "effective_from"}),
        indexes = @Index(
                name = "idx_schedule_assignments_org_employee",
                columnList = "organization_id, employee_id, effective_from"))
public class EmployeeScheduleAssignment extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_assignments_organization"))
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_assignments_employee"))
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_assignments_schedule"))
    private AttendanceSchedule schedule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected EmployeeScheduleAssignment() {
        // Required by JPA.
    }

    public EmployeeScheduleAssignment(
            Organization organization,
            Employee employee,
            AttendanceSchedule schedule,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {

        this.organization = organization;
        this.employee = employee;
        this.schedule = schedule;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Employee getEmployee() {
        return employee;
    }

    public AttendanceSchedule getSchedule() {
        return schedule;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void update(AttendanceSchedule schedule, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.schedule = schedule;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
