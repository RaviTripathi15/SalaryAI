package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.AuditRecordResponse;
import com.salaryai.salaryaiapi.attendance.dto.DailyAttendanceResponse;
import com.salaryai.salaryaiapi.attendance.dto.MonthlySummaryResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.dto.StatusCountResponse;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Read-only attendance queries, always scoped to the authenticated organization. */
@Service
@Transactional(readOnly = true)
public class AttendanceQueryService {

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final AttendanceAuditRecordRepository auditRecordRepository;
    private final AttendanceDomainSupport support;

    public AttendanceQueryService(
            DailyAttendanceRepository dailyAttendanceRepository,
            AttendanceAuditRecordRepository auditRecordRepository,
            AttendanceDomainSupport support) {

        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.support = support;
    }

    public PageResponse<DailyAttendanceResponse> listDaily(
            Long organizationId,
            Long employeeId,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceStatus status,
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        AttendanceRequestValidator.page(page, size);
        AttendanceRequestValidator.dateRange(fromDate, toDate);
        Sort sort = Sort.by(
                AttendanceRequestValidator.direction(sortDirection),
                AttendanceRequestValidator.sortField(sortBy).property());
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<DailyAttendanceResponse> mapped = dailyAttendanceRepository
                .findByOrganizationIdWithFilters(
                        organizationId, employeeId, fromDate, toDate, status, pageable)
                .map(DailyAttendanceResponse::from);

        return toPage(mapped);
    }

    public DailyAttendanceResponse dailyDetail(Long organizationId, Long employeeId, LocalDate date) {
        if (date == null) {
            throw new com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException(
                    "Work date is required.");
        }
        return dailyAttendanceRepository
                .findByOrganizationIdAndEmployeeIdAndWorkDate(organizationId, employeeId, date)
                .map(DailyAttendanceResponse::from)
                .orElseThrow(AttendanceNotFoundException::new);
    }

    public PageResponse<DailyAttendanceResponse> dailyOverview(
            Long organizationId, LocalDate date, int page, int size) {

        if (date == null) {
            throw new com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException(
                    "Work date is required.");
        }
        AttendanceRequestValidator.page(page, size);
        Page<DailyAttendanceResponse> mapped = dailyAttendanceRepository
                .findAllByOrganizationIdAndWorkDate(
                        organizationId, date, PageRequest.of(page, size))
                .map(DailyAttendanceResponse::from);
        return toPage(mapped);
    }

    public List<StatusCountResponse> statusCounts(Long organizationId, LocalDate date) {
        if (date == null) {
            throw new com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException(
                    "Work date is required.");
        }
        return dailyAttendanceRepository.countByStatus(organizationId, null, date, date)
                .stream()
                .map(row -> new StatusCountResponse(row.getStatus(), row.getTotal()))
                .toList();
    }

    public MonthlySummaryResponse monthlySummary(
            Long organizationId, Long employeeId, int year, int month) {

        AttendanceRequestValidator.yearMonth(year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        List<DailyAttendanceRepository.StatusCount> rows =
                dailyAttendanceRepository.countByStatus(organizationId, employeeId, from, to);

        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        long total = 0;
        for (DailyAttendanceRepository.StatusCount row : rows) {
            counts.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }

        return new MonthlySummaryResponse(
                year,
                month,
                employeeId,
                total,
                counts,
                counts.getOrDefault(AttendanceStatus.PRESENT, 0L)
                        + counts.getOrDefault(AttendanceStatus.LATE, 0L)
                        + counts.getOrDefault(AttendanceStatus.HALF_DAY, 0L),
                counts.getOrDefault(AttendanceStatus.LATE, 0L),
                counts.getOrDefault(AttendanceStatus.INCOMPLETE, 0L));
    }

    public PageResponse<AuditRecordResponse> audit(
            Long organizationId,
            String entityType,
            Long entityId,
            Instant fromTime,
            Instant toTime,
            int page,
            int size) {

        AttendanceRequestValidator.page(page, size);
        if (fromTime != null && toTime != null && toTime.isBefore(fromTime)) {
            throw new com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException(
                    "End time must not be before start time.");
        }
        Page<AuditRecordResponse> mapped = auditRecordRepository
                .findByOrganizationIdWithFilters(
                        organizationId, entityType, entityId, fromTime, toTime,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(AuditRecordResponse::from);
        return toPage(mapped);
    }

    private static <T> PageResponse<T> toPage(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
