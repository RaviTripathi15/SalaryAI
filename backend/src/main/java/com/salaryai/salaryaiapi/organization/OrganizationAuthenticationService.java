package com.salaryai.salaryaiapi.organization;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class OrganizationAuthenticationService {

    private final AuthenticationManager authenticationManager;

    public OrganizationAuthenticationService(
            @Qualifier("organizationAuthenticationManager")
            AuthenticationManager authenticationManager) {

        this.authenticationManager = authenticationManager;
    }

    public Authentication authenticate(
            String organizationName,
            String email,
            String password) {

        return authenticationManager.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        organizationName,
                        email,
                        password
                )
        );
    }
}
