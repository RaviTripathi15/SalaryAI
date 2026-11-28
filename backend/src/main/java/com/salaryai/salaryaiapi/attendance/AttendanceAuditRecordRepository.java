package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Organization-scoped immutable audit trail access. */
public interface AttendanceAuditRecordRepository extends JpaRepository<AttendanceAuditRecord, Long> {

    @Query(value = """
            SELECT a FROM AttendanceAuditRecord a
            WHERE a.organization.id = :organizationId
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:fromTime IS NULL OR a.occurredAt >= :fromTime)
              AND (:toTime IS NULL OR a.occurredAt <= :toTime)
            """, countQuery = """
            SELECT COUNT(a) FROM AttendanceAuditRecord a
            WHERE a.organization.id = :organizationId
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:fromTime IS NULL OR a.occurredAt >= :fromTime)
              AND (:toTime IS NULL OR a.occurredAt <= :toTime)
            """)
    Page<AttendanceAuditRecord> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);

    List<AttendanceAuditRecord> findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            Long organizationId, String entityType, Long entityId);
}
