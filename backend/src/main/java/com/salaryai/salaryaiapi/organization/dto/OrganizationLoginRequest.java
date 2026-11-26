package com.salaryai.salaryaiapi.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationLoginRequest(

        @NotBlank(message = "Organization name is required")
        String organizationName,

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password

) {}
