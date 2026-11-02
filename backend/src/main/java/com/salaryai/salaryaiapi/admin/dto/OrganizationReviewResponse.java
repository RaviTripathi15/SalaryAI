
package com.salaryai.salaryaiapi.admin.dto;

import java.time.LocalDateTime;

public record OrganizationReviewResponse(
        Long id,
        String name,
        String email,
        String phone,
        String status,
        LocalDateTime createdAt
) {}
