package com.salaryai.salaryaiapi.admin;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminAuthenticationService {

    private final AuthenticationManager authenticationManager;

    public PlatformAdminAuthenticationService(
            AuthenticationManager authenticationManager) {

        this.authenticationManager = authenticationManager;
    }

    public Authentication authenticate(
            String email,
            String password) {

        String normalizedEmail = email.trim().toLowerCase();

        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        normalizedEmail,
                        password
                )
        );
    }
}