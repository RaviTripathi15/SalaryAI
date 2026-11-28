package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.attendance.exception.UnauthorizedAttendanceAccessException;
import com.salaryai.salaryaiapi.organization.OrganizationPrincipal;
import org.springframework.security.core.Authentication;

/** Derives tenant identity and audit actor from the authenticated session only. */
public final class AttendanceRequestContext {

    private AttendanceRequestContext() {
    }

    public static Long organizationId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof OrganizationPrincipal principal)
                || principal.getId() == null) {
            throw new UnauthorizedAttendanceAccessException();
        }
        return principal.getId();
    }

    public static String actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof OrganizationPrincipal principal)) {
            throw new UnauthorizedAttendanceAccessException();
        }
        return principal.getEmail() == null ? principal.getName() : principal.getEmail();
    }
}
