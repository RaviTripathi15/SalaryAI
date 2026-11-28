package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "leave_types",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_types_org_name",
                columnNames = {"organization_id", "name"}))
public class LeaveType extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_leave_types_organization"))
    private Organization organization;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "paid", nullable = false)
    private boolean paid;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected LeaveType() {
        // Required by JPA.
    }

    public LeaveType(Organization organization, String name, boolean paid, boolean active) {
        this.organization = organization;
        this.name = name;
        this.paid = paid;
        this.active = active;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isActive() {
        return active;
    }

    public void update(boolean paid, boolean active) {
        this.paid = paid;
        this.active = active;
    }
}
