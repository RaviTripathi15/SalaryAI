package com.salaryai.salaryaiapi.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthenticationProviderTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OrganizationAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OrganizationAuthenticationProvider(
                organizationRepository,
                passwordEncoder
        );
    }

    @Test
    void approvedOrganizationAuthenticatesSuccessfully() {
        Organization organization = organization("Acme", "acme@example.com");
        organization.setStatus("APPROVED");

        stubLookup("acme@example.com", organization);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        Authentication result = provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme",
                        " ACME@Example.COM ",
                        "secret"
                )
        );

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal())
                .isInstanceOf(OrganizationPrincipal.class);
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ORGANIZATION_ADMIN");
    }

    @Test
    void legacyMixedCaseEmailAuthenticatesSuccessfully() {
        Organization legacy = organization("Legacy", "Legacy@Example.COM");
        legacy.setStatus("APPROVED");

        when(organizationRepository.findByEmailIgnoreCase("legacy@example.com"))
                .thenReturn(Optional.of(legacy));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        Authentication result = provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "legacy",
                        "LEGACY@example.com",
                        "secret"
                )
        );

        OrganizationPrincipal principal =
                (OrganizationPrincipal) result.getPrincipal();

        assertThat(principal.getEmail()).isEqualTo("Legacy@Example.COM");
        assertThat(principal.getRole()).isEqualTo("ROLE_ORGANIZATION_ADMIN");
    }

    @Test
    void ambiguousDuplicateEmailFailsClosedWithGenericBadCredentials() {
        when(organizationRepository.findByEmailIgnoreCase(anyString()))
                .thenThrow(new IncorrectResultSizeDataAccessException(2));

        assertThatThrownBy(() -> provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme",
                        "acme@example.com",
                        "secret"
                )
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid organization name, email or password.");
    }

    @Test
    void wrongOrganizationNameFailsWithGenericMessage() {
        Organization organization = organization("Acme", "acme@example.com");
        organization.setStatus("APPROVED");

        stubLookup("acme@example.com", organization);

        assertThatThrownBy(() -> provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Other Org",
                        "acme@example.com",
                        "secret"
                )
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid organization name, email or password.");
    }

    @Test
    void wrongEmailFailsWithGenericMessage() {
        when(organizationRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme",
                        "missing@example.com",
                        "secret"
                )
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid organization name, email or password.");
    }

    @Test
    void wrongPasswordFailsWithGenericMessage() {
        Organization organization = organization("Acme", "acme@example.com");
        organization.setStatus("APPROVED");

        stubLookup("acme@example.com", organization);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme",
                        "acme@example.com",
                        "wrong"
                )
        ))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid organization name, email or password.");
    }

    @Test
    void pendingOrganizationIsRejected() {
        assertNonApprovedStatusRejected("PENDING");
    }

    @Test
    void rejectedOrganizationIsRejected() {
        assertNonApprovedStatusRejected("REJECTED");
    }

    private void assertNonApprovedStatusRejected(String status) {
        Organization organization = organization("Acme", "acme@example.com");
        organization.setStatus(status);

        stubLookup("acme@example.com", organization);

        assertThatThrownBy(() -> provider.authenticate(
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme",
                        "acme@example.com",
                        "secret"
                )
        )).isInstanceOf(DisabledException.class);
    }

    private void stubLookup(String normalizedEmail, Organization organization) {
        when(organizationRepository.findByEmailIgnoreCase(normalizedEmail))
                .thenReturn(Optional.of(organization));
    }

    private static Organization organization(String name, String email) {
        Organization organization = new Organization(
                name,
                email,
                "5550100",
                "hash"
        );

        ReflectionTestUtils.setField(organization, "id", 42L);

        return organization;
    }
}
