package com.salaryai.salaryaiapi.organization;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Import(OrganizationApprovalSecurityChainTest.FutureOrganizationEndpoint.class)
class OrganizationApprovalSecurityChainTest {

    private static final long ORGANIZATION_ID = 7L;
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String SESSION_FAILURE_MESSAGE =
            "Your organization session is not active. Please sign in again.";
    private static final String WRONG_PRINCIPAL_MESSAGE =
            "This account is not authorized for the organization workspace.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    private final AtomicReference<String> currentStatus =
            new AtomicReference<>(APPROVED_STATUS);

    @BeforeEach
    void setUp() {
        reset(organizationRepository);
        currentStatus.set(APPROVED_STATUS);

        when(organizationRepository.existsByIdAndStatus(
                eq(ORGANIZATION_ID), eq(APPROVED_STATUS)))
                .thenAnswer(invocation ->
                        APPROVED_STATUS.equals(currentStatus.get())
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousMeReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(SESSION_FAILURE_MESSAGE));

        verifyNoInteractions(organizationRepository);
    }

    @Test
    void approvedOrganizationCanAccessMeAndFutureEndpoint() throws Exception {
        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.role").value("ORGANIZATION_ADMIN"));

        mockMvc.perform(
                get("/api/v1/organizations/future-test").session(session)
        )
                .andExpect(status().isOk())
                .andExpect(content().string("future-ok"));

        verify(organizationRepository, times(2)).existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS
        );
    }

    @Test
    void pendingOrganizationIsForbidden() throws Exception {
        currentStatus.set("PENDING");

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));

        verify(organizationRepository).existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS
        );
    }

    @Test
    void rejectedOrganizationIsForbidden() throws Exception {
        currentStatus.set("REJECTED");

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));
    }

    @Test
    void missingOrganizationIsForbidden() throws Exception {
        currentStatus.set(null);

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));
    }

    @Test
    void statusChangeInExistingSessionIsRevalidated() throws Exception {
        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isOk());

        currentStatus.set("REJECTED");

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));

        currentStatus.set("PENDING");

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isForbidden());

        currentStatus.set(APPROVED_STATUS);

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void repositoryFailureFailsClosedWithoutLeakingDetails() throws Exception {
        when(organizationRepository.existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE))
                .andExpect(jsonPath("$.error",
                        not(containsString("db down"))));
    }

    @Test
    void platformAdminSessionIsForbiddenForOrganizationEndpointButAdminEndpointStillWorks()
            throws Exception {

        MockHttpSession session = sessionFor(platformAdminAuthentication());

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));

        mockMvc.perform(
                get("/api/v1/platform-admin/organizations/counts")
                        .session(session)
        ).andExpect(status().isOk());

        verify(organizationRepository, never()).existsByIdAndStatus(
                anyLong(), anyString()
        );
    }

    @Test
    void organizationSessionIsForbiddenForAdminEndpointAndStillAllowedForOrganizationEndpoint()
            throws Exception {

        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(
                get("/api/v1/platform-admin/organizations/counts")
                        .session(session)
        ).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void otherAuthenticatedPrincipalIsForbiddenForOrganizationEndpoint()
            throws Exception {

        Authentication other = UsernamePasswordAuthenticationToken.authenticated(
                "someone@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(sessionFor(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error")
                        .value(WRONG_PRINCIPAL_MESSAGE));

        verify(organizationRepository, never()).existsByIdAndStatus(
                anyLong(), anyString()
        );
    }

    @Test
    void downgradedOrganizationCanStillLogoutAndCannotReaccessMe()
            throws Exception {

        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(get("/api/v1/organizations/me").session(session))
                .andExpect(status().isOk());

        currentStatus.set("REJECTED");
        CsrfTokens csrf = freshCsrfTokens();
        String oldSessionId = session.getId();

        MvcResult logoutResult = mockMvc.perform(
                post("/api/v1/organizations/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Logged out successfully."))
                .andReturn();

        assertThat(session.isInvalid()).isTrue();

        Cookie expiredCookie =
                logoutResult.getResponse().getCookie(CSRF_COOKIE);
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getValue()).isEmpty();
        assertThat(expiredCookie.getMaxAge()).isZero();

        MockHttpSession replayedSession =
                new MockHttpSession(new MockServletContext(), oldSessionId);

        mockMvc.perform(get("/api/v1/organizations/me")
                        .session(replayedSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(SESSION_FAILURE_MESSAGE));
    }

    @Test
    void loginRemainsPublicAndIsNotEvaluatedByApprovalManager()
            throws Exception {

        when(organizationRepository.existsByIdAndStatus(
                anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException(
                        "manager should not run"
                ));

        Organization stored = org.mockito.Mockito.mock(Organization.class);
        when(stored.getId()).thenReturn(ORGANIZATION_ID);
        when(stored.getName()).thenReturn("Acme");
        when(stored.getEmail()).thenReturn("acme@example.com");
        when(stored.getStatus()).thenReturn(APPROVED_STATUS);
        when(stored.getPasswordHash())
                .thenReturn(passwordEncoder.encode("secret123"));

        when(organizationRepository.findByEmailIgnoreCase(
                "acme@example.com"))
                .thenReturn(Optional.of(stored));

        CsrfTokens csrf = freshCsrfTokens();

        mockMvc.perform(
                post("/api/v1/organizations/login")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson())
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORGANIZATION_ID))
                .andExpect(jsonPath("$.role").value("ORGANIZATION_ADMIN"));

        verify(organizationRepository, never()).existsByIdAndStatus(
                anyLong(), anyString()
        );
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(
                post("/api/v1/organizations/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson())
        ).andExpect(status().isForbidden());
    }

    @Test
    void registrationRemainsPublicWithCsrfAndIsNotEvaluatedByApprovalManager()
            throws Exception {

        when(organizationRepository.existsByIdAndStatus(
                anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException(
                        "manager should not run"
                ));
        when(organizationRepository.existsByEmailIgnoreCase(
                "new@example.com"))
                .thenReturn(false);
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CsrfTokens csrf = freshCsrfTokens();

        mockMvc.perform(
                post("/api/v1/organizations/register")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Org",
                                  "email": "new@example.com",
                                  "phone": "555-0100",
                                  "password": "secret123"
                                }
                                """)
        )
                .andExpect(status().isCreated())
                .andExpect(content().string(
                        "Organization registration submitted successfully. "
                                + "Awaiting approval."
                ));

        verify(organizationRepository, never()).existsByIdAndStatus(
                anyLong(), anyString()
        );
    }

    @Test
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/logout")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void clientSuppliedOrganizationIdIsIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/me")
                        .param("organizationId", "999")
                        .header("X-Organization-Id", "999")
                        .session(sessionFor(organizationAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORGANIZATION_ID));

        verify(organizationRepository).existsByIdAndStatus(
                ORGANIZATION_ID, APPROVED_STATUS
        );
        verify(organizationRepository, never()).existsByIdAndStatus(
                999L, APPROVED_STATUS
        );
    }

    private CsrfTokens freshCsrfTokens() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();

        return new CsrfTokens(cookie, cookie.getValue());
    }

    private static String loginJson() {
        return """
                {
                  "organizationName": "Acme",
                  "email": "acme@example.com",
                  "password": "secret123"
                }
                """;
    }

    private static Authentication organizationAuthentication() {
        return OrganizationLoginAuthenticationToken.authenticated(
                new OrganizationPrincipal(
                        ORGANIZATION_ID,
                        "Acme",
                        "acme@example.com"
                )
        );
    }

    private static Authentication platformAdminAuthentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
    }

    private static MockHttpSession sessionFor(
            Authentication authentication) {

        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );

        return session;
    }

    private record CsrfTokens(Cookie cookie, String token) {
    }

    @RestController
    @RequestMapping("/api/v1/organizations/future-test")
    static class FutureOrganizationEndpoint {

        @GetMapping
        String get() {
            return "future-ok";
        }
    }
}
