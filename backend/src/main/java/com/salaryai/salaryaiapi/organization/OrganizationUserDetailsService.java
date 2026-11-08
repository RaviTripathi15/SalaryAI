
package com.salaryai.salaryaiapi.organization;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

@Service
public class OrganizationUserDetailsService
        implements UserDetailsService {

    private final OrganizationRepository organizationRepository;

    public OrganizationUserDetailsService(
            OrganizationRepository organizationRepository) {

        this.organizationRepository = organizationRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email.trim().toLowerCase();

        Organization organization = organizationRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid email or password."
                ));

        if (!organization.getStatus().equals("APPROVED")) {
            throw new DisabledException(
                    "This organization is not approved for login."
            );
        }

        return User.withUsername(organization.getEmail())
                .password(organization.getPasswordHash())
                .roles("ORGANIZATION_ADMIN")
                .build();
    }
}
