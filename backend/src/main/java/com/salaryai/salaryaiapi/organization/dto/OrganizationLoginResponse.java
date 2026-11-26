package com.salaryai.salaryaiapi.organization.dto;

import com.salaryai.salaryaiapi.organization.OrganizationPrincipal;

public record OrganizationLoginResponse(
        Long id,
        String name,
        String email,
        String role
) {

    private static final String ROLE_PREFIX = "ROLE_";

    public static OrganizationLoginResponse from(
            OrganizationPrincipal principal) {

        String role = principal.getRole();

        if (role.startsWith(ROLE_PREFIX)) {
            role = role.substring(ROLE_PREFIX.length());
        }

        return new OrganizationLoginResponse(
                principal.getId(),
                principal.getName(),
                principal.getEmail(),
                role
        );
    }
}
