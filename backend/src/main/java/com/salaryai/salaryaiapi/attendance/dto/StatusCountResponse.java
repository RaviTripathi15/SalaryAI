package com.salaryai.salaryaiapi.attendance.dto;

import com.salaryai.salaryaiapi.attendance.AttendanceStatus;

public record StatusCountResponse(AttendanceStatus status, long total) {
}
