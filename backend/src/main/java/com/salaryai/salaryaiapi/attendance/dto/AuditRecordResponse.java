package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceAuditRecord;
import com.salaryai.salaryaiapi.attendance.AuditAction;

import java.time.Instant;

public record AuditRecordResponse(
        Long id,
        String entityType,
        Long entityId,
        AuditAction action,
        String actor,
        String detail,
        Instant occurredAt) {

    public static AuditRecordResponse from(AttendanceAuditRecord record) {
        return new AuditRecordResponse(
                record.getId(),
                record.getEntityType(),
                record.getEntityId(),
                record.getAction(),
                record.getActor(),
                record.getDetail(),
                record.getOccurredAt());
    }
}
