package com.salaryai.salaryaiapi.attendance.dto;

import jakarta.validation.constraints.Min;

public record LeaveDecisionRequest(@Min(0) long version) {
}
