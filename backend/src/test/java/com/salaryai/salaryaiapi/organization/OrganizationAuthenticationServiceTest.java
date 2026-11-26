package com.salaryai.salaryaiapi.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthenticationServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    private OrganizationAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService =
                new OrganizationAuthenticationService(authenticationManager);
    }

    @Test
    void authenticatesUsingExistingUnauthenticatedOrganizationToken() {
        OrganizationPrincipal principal =
                new OrganizationPrincipal(1L, "Acme", "acme@example.com");

        Authentication expected =
                OrganizationLoginAuthenticationToken.authenticated(principal);

        when(authenticationManager.authenticate(
                org.mockito.ArgumentMatchers.any(Authentication.class)))
                .thenReturn(expected);

        Authentication actual = authenticationService.authenticate(
                "Acme",
                "ACME@Example.COM",
                "secret"
        );

        ArgumentCaptor<Authentication> captor =
                ArgumentCaptor.forClass(Authentication.class);

        verify(authenticationManager).authenticate(captor.capture());

        Authentication submitted = captor.getValue();

        assertThat(submitted)
                .isInstanceOf(OrganizationLoginAuthenticationToken.class);
        assertThat(submitted.isAuthenticated()).isFalse();
        assertThat(submitted.getPrincipal()).isEqualTo("ACME@Example.COM");
        assertThat(submitted.getCredentials()).isEqualTo("secret");

        OrganizationLoginAuthenticationToken token =
                (OrganizationLoginAuthenticationToken) submitted;

        assertThat(token.getOrganizationName()).isEqualTo("Acme");
        assertThat(token.getEmail()).isEqualTo("ACME@Example.COM");
        assertThat(actual).isSameAs(expected);
    }
}
