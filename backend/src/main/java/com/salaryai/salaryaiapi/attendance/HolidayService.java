package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.HolidayRequest;
import com.salaryai.salaryaiapi.attendance.dto.HolidayResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import com.salaryai.salaryaiapi.attendance.exception.AttendanceConflictException;
import com.salaryai.salaryaiapi.attendance.exception.InvalidAttendanceRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AttendanceDomainSupport support;

    public HolidayService(HolidayRepository holidayRepository, AttendanceDomainSupport support) {
        this.holidayRepository = holidayRepository;
        this.support = support;
    }

    public HolidayResponse create(Long organizationId, String actor, HolidayRequest request) {
        validate(request);
        Holiday holiday = new Holiday(
                support.organizationRef(organizationId),
                request.name(),
                request.holidayDate(),
                request.description(),
                request.calendarName());
        Holiday saved = flush(() -> holidayRepository.saveAndFlush(holiday));
        support.audit(organizationId, "HOLIDAY", saved.getId(),
                AuditAction.HOLIDAY_CREATED, actor, "Holiday " + saved.getName() + " created.");
        return HolidayResponse.from(saved);
    }

    public HolidayResponse update(
            Long organizationId, Long holidayId, String actor, HolidayRequest request) {

        Holiday holiday = requireHoliday(organizationId, holidayId);
        support.validateVersion(request.version(), holiday.getVersion());
        validate(request);
        if (holidayRepository.existsByOrganizationIdAndHolidayDateAndIdNot(
                organizationId, request.holidayDate(), holidayId)) {
            throw new AttendanceConflictException("A holiday already exists on this date.");
        }
        holiday.update(
                request.name(), request.holidayDate(), request.description(), request.calendarName());
        Holiday saved = flush(() -> holidayRepository.saveAndFlush(holiday));
        support.audit(organizationId, "HOLIDAY", saved.getId(),
                AuditAction.HOLIDAY_UPDATED, actor, "Holiday updated.");
        return HolidayResponse.from(saved);
    }

    public void delete(Long organizationId, Long holidayId, String actor, Long version) {
        Holiday holiday = requireHoliday(organizationId, holidayId);
        support.validateVersion(version, holiday.getVersion());
        if (holiday.getHolidayDate().isBefore(LocalDate.now())) {
            throw new AttendanceConflictException(
                    "Past holidays are retained for attendance history and cannot be deleted.");
        }
        holidayRepository.delete(holiday);
        holidayRepository.flush();
        support.audit(organizationId, "HOLIDAY", holidayId,
                AuditAction.HOLIDAY_DELETED, actor, "Holiday deleted.");
    }

    @Transactional(readOnly = true)
    public PageResponse<HolidayResponse> list(
            Long organizationId, LocalDate fromDate, LocalDate toDate, int page, int size) {

        AttendanceRequestValidator.page(page, size);
        AttendanceRequestValidator.dateRange(fromDate, toDate);
        Page<HolidayResponse> mapped = holidayRepository
                .findByOrganizationIdWithFilters(
                        organizationId, fromDate, toDate,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "holidayDate")))
                .map(HolidayResponse::from);
        return new PageResponse<>(
                mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements(), mapped.getTotalPages(), mapped.isFirst(), mapped.isLast());
    }

    private Holiday requireHoliday(Long organizationId, Long holidayId) {
        support.requireNonNullId(holidayId);
        return holidayRepository.findByIdAndOrganizationId(holidayId, organizationId)
                .orElseThrow(com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException::new);
    }

    private static void validate(HolidayRequest request) {
        if (request == null || request.holidayDate() == null) {
            throw new InvalidAttendanceRequestException("Holiday date is required.");
        }
    }

    private static <T> T flush(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OptimisticLockingFailureException exception) {
            throw new com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException();
        } catch (DataIntegrityViolationException exception) {
            throw new AttendanceConflictException("A holiday already exists on this date.");
        }
    }
}
