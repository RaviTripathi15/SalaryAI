package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.dto.LeaveRequestCreate;
import com.salaryai.salaryaiapi.attendance.dto.LeaveRequestResponse;
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
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceDomainSupport support;

    public LeaveService(
            LeaveRequestRepository leaveRequestRepository,
            AttendanceDomainSupport support) {

        this.leaveRequestRepository = leaveRequestRepository;
        this.support = support;
    }

    public LeaveRequestResponse create(
            Long organizationId, String actor, LeaveRequestCreate request) {

        if (request == null) {
            throw new InvalidAttendanceRequestException("Leave request is required.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidAttendanceRequestException("Reason is required.");
        }
        AttendanceRequestValidator.dateRange(request.startDate(), request.endDate());
        if (request.startDate() == null || request.endDate() == null) {
            throw new InvalidAttendanceRequestException("Leave dates are required.");
        }

        var employee = support.requireEmployee(organizationId, request.employeeId());
        var leaveType = support.requireLeaveType(organizationId, request.leaveTypeId());
        if (!leaveType.isActive()) {
            throw new AttendanceConflictException("This leave type is not active.");
        }
        if (!leaveRequestRepository.findActiveOverlaps(
                organizationId, employee.getId(), request.startDate(), request.endDate(), null)
                .isEmpty()) {
            throw new AttendanceConflictException(
                    "An overlapping pending or approved leave request already exists.");
        }

        LeaveRequest leaveRequest = new LeaveRequest(
                support.organizationRef(organizationId),
                employee,
                leaveType,
                request.startDate(),
                request.endDate(),
                request.reason());

        LeaveRequest saved;
        try {
            saved = leaveRequestRepository.saveAndFlush(leaveRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new AttendanceConflictException("Conflicting leave request.");
        }
        support.audit(organizationId, "LEAVE_REQUEST", saved.getId(),
                AuditAction.LEAVE_REQUEST_CREATED, actor,
                "Leave requested from " + saved.getStartDate() + " to " + saved.getEndDate() + ".");
        return LeaveRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> list(
            Long organizationId,
            Long employeeId,
            LeaveStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {

        AttendanceRequestValidator.page(page, size);
        AttendanceRequestValidator.dateRange(fromDate, toDate);
        Page<LeaveRequestResponse> mapped = leaveRequestRepository
                .findByOrganizationIdWithFilters(
                        organizationId, employeeId, status, fromDate, toDate,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate")))
                .map(LeaveRequestResponse::from);
        return new PageResponse<>(
                mapped.getContent(), mapped.getNumber(), mapped.getSize(),
                mapped.getTotalElements(), mapped.getTotalPages(), mapped.isFirst(), mapped.isLast());
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse get(Long organizationId, Long leaveRequestId) {
        return LeaveRequestResponse.from(requireRequest(organizationId, leaveRequestId));
    }

    public LeaveRequestResponse approve(
            Long organizationId, Long leaveRequestId, String actor, long version) {

        LeaveRequest request = requireRequest(organizationId, leaveRequestId);
        support.validateVersion(version, request.getVersion());
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new AttendanceConflictException("Only pending leave requests can be decided.");
        }
        request.decide(LeaveStatus.APPROVED, actor);
        LeaveRequest saved = flush(request);
        support.audit(organizationId, "LEAVE_REQUEST", saved.getId(),
                AuditAction.LEAVE_APPROVED, actor, "Leave request approved.");
        return LeaveRequestResponse.from(saved);
    }

    public LeaveRequestResponse reject(
            Long organizationId, Long leaveRequestId, String actor, long version) {

        LeaveRequest request = requireRequest(organizationId, leaveRequestId);
        support.validateVersion(version, request.getVersion());
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new AttendanceConflictException("Only pending leave requests can be decided.");
        }
        request.decide(LeaveStatus.REJECTED, actor);
        LeaveRequest saved = flush(request);
        support.audit(organizationId, "LEAVE_REQUEST", saved.getId(),
                AuditAction.LEAVE_REJECTED, actor, "Leave request rejected.");
        return LeaveRequestResponse.from(saved);
    }

    public LeaveRequestResponse cancel(
            Long organizationId, Long leaveRequestId, String actor, long version) {

        LeaveRequest request = requireRequest(organizationId, leaveRequestId);
        support.validateVersion(version, request.getVersion());
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new AttendanceConflictException("Only pending leave requests can be cancelled.");
        }
        request.decide(LeaveStatus.CANCELLED, actor);
        LeaveRequest saved = flush(request);
        support.audit(organizationId, "LEAVE_REQUEST", saved.getId(),
                AuditAction.LEAVE_CANCELLED, actor, "Leave request cancelled.");
        return LeaveRequestResponse.from(saved);
    }

    private LeaveRequest requireRequest(Long organizationId, Long leaveRequestId) {
        support.requireNonNullId(leaveRequestId);
        return leaveRequestRepository.findByIdAndOrganizationId(leaveRequestId, organizationId)
                .orElseThrow(com.salaryai.salaryaiapi.attendance.exception.AttendanceNotFoundException::new);
    }

    private LeaveRequest flush(LeaveRequest request) {
        try {
            return leaveRequestRepository.saveAndFlush(request);
        } catch (OptimisticLockingFailureException exception) {
            throw new com.salaryai.salaryaiapi.attendance.exception.StaleAttendanceVersionException();
        }
    }
}
