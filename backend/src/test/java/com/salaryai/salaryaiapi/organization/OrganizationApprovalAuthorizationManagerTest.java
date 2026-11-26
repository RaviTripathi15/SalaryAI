package com.salaryai.salaryaiapi.organization;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationApprovalAuthorizationManagerTest {

    private static final long ORGANIZATION_ID = 7L;
    private static final String APPROVED_STATUS = "APPROVED";

    @Mock
    private OrganizationRepository organizationRepository;

    private OrganizationApprovalAuthorizationManager authorizationManager;

    @BeforeEach
    void setUp() {
        authorizationManager =
                new OrganizationApprovalAuthorizationManager(
                        organizationRepository
                );
    }

    @Test
    void approvedOrganizationIsGranted() {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS)).thenReturn(true);

        AuthorizationResult result = authorize(
                organizationAuthentication(ORGANIZATION_ID),
                context()
        );

        assertGranted(result);
        verify(organizationRepository).existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS
        );
    }

    @Test
    void pendingOrganizationIsDenied() {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS)).thenReturn(false);

        assertDenied(authorize(
                organizationAuthentication(ORGANIZATION_ID),
                context()
        ));
    }

    @Test
    void rejectedOrganizationIsDenied() {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS)).thenReturn(false);

        assertDenied(authorize(
                organizationAuthentication(ORGANIZATION_ID),
                context()
        ));
    }

    @Test
    void missingOrganizationIsDenied() {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS)).thenReturn(false);

        assertDenied(authorize(
                organizationAuthentication(ORGANIZATION_ID),
                context()
        ));
    }

    @Test
    void repositoryFailureIsDenied() {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertDenied(authorize(
                organizationAuthentication(ORGANIZATION_ID),
                context()
        ));
    }

    @Test
    void missingAuthenticationIsDenied() {
        assertDenied(authorize(null, context()));
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void anonymousAuthenticationIsDenied() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        assertDenied(authorize(anonymous, context()));
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void unauthenticatedOrganizationTokenIsDenied() {
        Authentication unauthenticated =
                OrganizationLoginAuthenticationToken.unauthenticated(
                        "Acme", "acme@example.com", "secret"
                );

        assertDenied(authorize(unauthenticated, context()));
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void wrongPrincipalIsDenied() {
        Authentication admin = UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );

        assertDenied(authorize(admin, context()));
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void missingOrganizationRoleIsDenied() {
        Authentication wrongRole = UsernamePasswordAuthenticationToken.authenticated(
                new OrganizationPrincipal(
                        ORGANIZATION_ID,
                        "Acme",
                        "acme@example.com"
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertDenied(authorize(wrongRole, context()));
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void authenticationSupplierFailureIsDenied() {
        AuthorizationResult result = authorizationManager.authorize(
                () -> { throw new IllegalStateException("resolver failed"); },
                context()
        );

        assertDenied(result);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void clientSuppliedOrganizationIdIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("organizationId", "999");
        request.addHeader("X-Organization-Id", "999");

        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS)).thenReturn(true);

        assertGranted(authorize(
                organizationAuthentication(ORGANIZATION_ID),
                new RequestAuthorizationContext(request)
        ));

        verify(organizationRepository).existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS
        );
        verify(organizationRepository, never()).existsByIdAndStatus(
                999L, APPROVED_STATUS
        );
    }

    private AuthorizationResult authorize(
            Authentication authentication,
            RequestAuthorizationContext context) {

        return authorizationManager.authorize(() -> authentication, context);
    }

    private static RequestAuthorizationContext context() {
        return new RequestAuthorizationContext(new MockHttpServletRequest());
    }

    private static Authentication organizationAuthentication(long id) {
        return OrganizationLoginAuthenticationToken.authenticated(
                new OrganizationPrincipal(
                        id,
                        "Acme",
                        "acme@example.com"
                )
        );
    }

    private static void assertGranted(AuthorizationResult result) {
        assertThat(result).isInstanceOf(AuthorizationDecision.class);
        assertThat(((AuthorizationDecision) result).isGranted()).isTrue();
    }

    private static void assertDenied(AuthorizationResult result) {
        assertThat(result).isInstanceOf(AuthorizationDecision.class);
        assertThat(((AuthorizationDecision) result).isGranted()).isFalse();
    }
}
