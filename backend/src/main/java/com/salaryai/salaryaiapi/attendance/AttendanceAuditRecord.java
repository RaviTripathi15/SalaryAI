package com.salaryai.salaryaiapi.attendance;

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

/** Append-only audit trail for attendance decisions and changes. */
@Entity
@Table(
        name = "attendance_audit_records",
        indexes = {
                @Index(name = "idx_audit_records_org_time",
                        columnList = "organization_id, occurred_at"),
                @Index(name = "idx_audit_records_org_entity",
                        columnList = "organization_id, entity_type, entity_id")
        })
public class AttendanceAuditRecord extends AbstractImmutableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_audit_records_organization"))
    private Organization organization;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "actor", nullable = false, length = 255)
    private String actor;

    @Column(name = "detail", nullable = false, length = 1000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AttendanceAuditRecord() {
        // Required by JPA.
    }

    public AttendanceAuditRecord(
            Organization organization,
            String entityType,
            Long entityId,
            AuditAction action,
            String actor,
            String detail) {

        this.organization = organization;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.detail = detail;
        this.occurredAt = Instant.now();
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
