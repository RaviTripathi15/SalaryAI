package com.salaryai.salaryaiapi.organization;

import java.util.Locale;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAuthenticationProvider
        implements AuthenticationProvider {

    private static final String APPROVED_STATUS = "APPROVED";

    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationAuthenticationProvider(
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder) {

        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        if (!(authentication instanceof OrganizationLoginAuthenticationToken token)) {
            return null;
        }

        String submittedName = token.getOrganizationName();
        String submittedEmail = token.getEmail();
        Object credentials = token.getCredentials();

        if (submittedName == null || submittedName.isBlank()
                || submittedEmail == null || submittedEmail.isBlank()
                || !(credentials instanceof String rawPassword)
                || rawPassword.isEmpty()) {

            throw new BadCredentialsException(
                    "Invalid organization name, email or password."
            );
        }

        String normalizedEmail =
                submittedEmail.trim().toLowerCase(Locale.ROOT);

        Organization organization;

        try {
            organization = organizationRepository
                    .findByEmailIgnoreCase(normalizedEmail)
                    .orElseThrow(() -> new BadCredentialsException(
                            "Invalid organization name, email or password."
                    ));
        } catch (IncorrectResultSizeDataAccessException exception) {
            throw new BadCredentialsException(
                    "Invalid organization name, email or password."
            );
        }

        if (!submittedName.trim().equalsIgnoreCase(
                organization.getName() == null
                        ? null
                        : organization.getName().trim())) {

            throw new BadCredentialsException(
                    "Invalid organization name, email or password."
            );
        }

        if (!APPROVED_STATUS.equals(organization.getStatus())) {
            throw new DisabledException(
                    "This organization is not approved for login."
            );
        }

        if (!passwordEncoder.matches(
                rawPassword, organization.getPasswordHash())) {

            throw new BadCredentialsException(
                    "Invalid organization name, email or password."
            );
        }

        OrganizationPrincipal principal = new OrganizationPrincipal(
                organization.getId(),
                organization.getName(),
                organization.getEmail()
        );

        return OrganizationLoginAuthenticationToken.authenticated(principal);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OrganizationLoginAuthenticationToken.class.equals(authentication);
    }
}
