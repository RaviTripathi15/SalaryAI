package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceCorrection;
import com.salaryai.salaryaiapi.attendance.AttendanceStatus;

import java.time.Instant;

public record CorrectionResponse(
        Long id,
        Long dailyAttendanceId,
        Long employeeId,
        String reason,
        AttendanceStatus previousStatus,
        AttendanceStatus correctedStatus,
        Instant previousCheckIn,
        Instant previousCheckOut,
        Instant correctedCheckIn,
        Instant correctedCheckOut,
        long expectedVersion,
        String actor,
        Instant correctedAt,
        boolean payrollLocked) {

    public static CorrectionResponse from(AttendanceCorrection correction) {
        return new CorrectionResponse(
                correction.getId(),
                correction.getDailyAttendance().getId(),
                correction.getEmployee().getId(),
                correction.getReason(),
                correction.getPreviousStatus(),
                correction.getCorrectedStatus(),
                correction.getPreviousCheckIn(),
                correction.getPreviousCheckOut(),
                correction.getCorrectedCheckIn(),
                correction.getCorrectedCheckOut(),
                correction.getExpectedVersion(),
                correction.getActor(),
                correction.getCorrectedAt(),
                correction.isPayrollLocked());
    }
}
