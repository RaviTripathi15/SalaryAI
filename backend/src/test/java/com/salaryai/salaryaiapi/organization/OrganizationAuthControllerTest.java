package com.salaryai.salaryaiapi.organization;

import com.salaryai.salaryaiapi.organization.dto.OrganizationLoginResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationAuthControllerTest {

    @Mock
    private OrganizationAuthenticationService authenticationService;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private CsrfTokenRepository csrfTokenRepository;

    private RecordingSessionStrategy sessionAuthenticationStrategy;

    private final List<String> events = new ArrayList<>();

    private MockMvc mockMvc;

    private OrganizationAuthController controller;

    private Authentication authenticated;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        events.clear();

        sessionAuthenticationStrategy =
                new RecordingSessionStrategy(events);

        OrganizationPrincipal principal =
                new OrganizationPrincipal(7L, "Acme", "acme@example.com");

        authenticated =
                OrganizationLoginAuthenticationToken.authenticated(principal);

        controller = new OrganizationAuthController(
                authenticationService,
                sessionAuthenticationStrategy,
                securityContextRepository,
                csrfTokenRepository
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulLoginRotatesExistingSessionIdAndSavesContext()
            throws Exception {

        stubSuccessfulAuthentication();
        stubCsrfDeletionEvent();
        stubContextSaveEvent();

        MockHttpSession originalSession = new MockHttpSession();
        String originalSessionId = originalSession.getId();

        MvcResult result = mockMvc.perform(
                post("/api/v1/organizations/login")
                        .session(originalSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("secret"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.email").value("acme@example.com"))
                .andExpect(jsonPath("$.role")
                        .value("ORGANIZATION_ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String rotatedSessionId = result.getRequest()
                .getSession(false)
                .getId();

        assertThat(rotatedSessionId)
                .isNotBlank()
                .isNotEqualTo(originalSessionId);

        assertThat(events).containsExactly(
                "authentication",
                "session-strategy",
                "csrf-expired",
                "context-saved"
        );

        assertThat(sessionAuthenticationStrategy.authentication)
                .isSameAs(authenticated);
    }

    @Test
    void successfulLoginCreatesSessionWhenNoneExists() throws Exception {
        stubSuccessfulAuthentication();

        MvcResult result = mockMvc.perform(
                post("/api/v1/organizations/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("secret"))
        )
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getId())
                .isNotBlank();
    }

    @Test
    void failedLoginDoesNotEstablishAuthenticatedSession()
            throws Exception {

        when(authenticationService.authenticate(
                "Acme", "acme@example.com", "wrong"))
                .thenThrow(new BadCredentialsException("internal detail"));

        MvcResult result = mockMvc.perform(
                post("/api/v1/organizations/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong"))
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("Invalid organization name, email or password."))
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "internal detail"))))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        assertThat(events).isEmpty();

        verifyNoInteractions(
                securityContextRepository,
                csrfTokenRepository
        );
    }

    @Test
    void disabledOrganizationFailureReturnsSameGenericUnauthorizedResponse()
            throws Exception {

        when(authenticationService.authenticate(
                "Acme", "acme@example.com", "secret"))
                .thenThrow(new DisabledException(
                        "This organization is not approved for login."));

        mockMvc.perform(
                post("/api/v1/organizations/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("secret"))
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("Invalid organization name, email or password."));

        assertThat(events).isEmpty();

        verifyNoInteractions(
                securityContextRepository,
                csrfTokenRepository
        );
    }

    @Test
    void currentOrganizationReturnsSafeResponseForOrganizationPrincipal() {
        ResponseEntity<?> response =
                controller.getCurrentOrganization(authenticated);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(new OrganizationLoginResponse(
                        7L,
                        "Acme",
                        "acme@example.com",
                        "ORGANIZATION_ADMIN"
                ));
    }

    @Test
    void currentOrganizationRejectsMissingAuthentication() {
        assertMeError(
                null,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void currentOrganizationRejectsAnonymousAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        assertMeError(
                anonymous,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void currentOrganizationRejectsUnauthenticatedToken() {
        Authentication unauthenticated =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        authenticated.getPrincipal(),
                        null
                );

        assertMeError(
                unauthenticated,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void currentOrganizationRejectsPlatformAdminPrincipal() {
        Authentication platformAdmin =
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                );

        assertMeError(
                platformAdmin,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    @Test
    void currentOrganizationRejectsOtherPrincipalType() {
        Authentication otherPrincipal =
                UsernamePasswordAuthenticationToken.authenticated(
                        "someone@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        assertMeError(
                otherPrincipal,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    @Test
    void currentOrganizationRejectsOrganizationPrincipalWithoutAuthority() {
        Authentication missingAuthority =
                UsernamePasswordAuthenticationToken.authenticated(
                        authenticated.getPrincipal(),
                        null,
                        List.of()
                );

        assertMeError(
                missingAuthority,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    @Test
    void logoutInvalidatesSessionClearsContextAndExpiresCsrfCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);

        ResponseEntity<?> result =
                controller.logout(authenticated, request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody())
                .isEqualTo(Map.of("message", "Logged out successfully."));
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verify(csrfTokenRepository).saveToken(
                isNull(),
                eq(request),
                eq(response)
        );
    }

    @Test
    void logoutRejectsMissingAuthentication() {
        assertLogoutRejected(
                null,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void logoutRejectsUnauthenticatedToken() {
        Authentication unauthenticated =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        authenticated.getPrincipal(),
                        null
                );

        assertLogoutRejected(
                unauthenticated,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void logoutRejectsAnonymousAuthentication() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        assertLogoutRejected(
                anonymous,
                HttpStatus.UNAUTHORIZED,
                "Your organization session is not active. Please sign in again."
        );
    }

    @Test
    void logoutRejectsPlatformAdminPrincipal() {
        Authentication platformAdmin =
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                );

        assertLogoutRejected(
                platformAdmin,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    @Test
    void logoutRejectsOtherPrincipalType() {
        Authentication otherPrincipal =
                UsernamePasswordAuthenticationToken.authenticated(
                        "someone@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        assertLogoutRejected(
                otherPrincipal,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    @Test
    void logoutRejectsOrganizationPrincipalWithoutAuthority() {
        Authentication missingAuthority =
                UsernamePasswordAuthenticationToken.authenticated(
                        authenticated.getPrincipal(),
                        null,
                        List.of()
                );

        assertLogoutRejected(
                missingAuthority,
                HttpStatus.FORBIDDEN,
                "This account is not authorized for the organization workspace."
        );
    }

    private void assertLogoutRejected(
            Authentication authentication,
            HttpStatus expectedStatus,
            String expectedMessage) {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        ResponseEntity<?> result =
                controller.logout(authentication, request, response);

        assertThat(result.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(result.getBody())
                .isEqualTo(Map.of("error", expectedMessage));
        assertThat(session.isInvalid()).isFalse();

        verifyNoInteractions(csrfTokenRepository);
    }

    private void assertMeError(
            Authentication authentication,
            HttpStatus expectedStatus,
            String expectedMessage) {

        ResponseEntity<?> response =
                controller.getCurrentOrganization(authentication);

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody())
                .isEqualTo(Map.of("error", expectedMessage));
    }

    private void stubSuccessfulAuthentication() {
        when(authenticationService.authenticate(
                "Acme", "acme@example.com", "secret"))
                .thenAnswer(invocation -> {
                    events.add("authentication");
                    return authenticated;
                });
    }

    private void stubCsrfDeletionEvent() {
        doAnswer(invocation -> {
            events.add("csrf-expired");
            return null;
        }).when(csrfTokenRepository).saveToken(
                isNull(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    private void stubContextSaveEvent() {
        doAnswer(invocation -> {
            events.add("context-saved");
            return null;
        }).when(securityContextRepository).saveContext(
                any(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    private static String loginJson(String password) {
        return """
                {
                  "organizationName": "Acme",
                  "email": "acme@example.com",
                  "password": "%s"
                }
                """.formatted(password);
    }

    private static final class RecordingSessionStrategy
            implements SessionAuthenticationStrategy {

        private final ChangeSessionIdAuthenticationStrategy delegate;
        private final List<String> events;

        private Authentication authentication;

        private RecordingSessionStrategy(List<String> events) {
            delegate = new ChangeSessionIdAuthenticationStrategy();
            delegate.setAlwaysCreateSession(true);
            this.events = events;
        }

        @Override
        public void onAuthentication(
                Authentication authentication,
                HttpServletRequest request,
                HttpServletResponse response) {

            this.authentication = authentication;
            events.add("session-strategy");
            delegate.onAuthentication(authentication, request, response);
        }
    }
}

// Full security-chain tests exercise the real SecurityConfig filter chain and
// HttpSessionSecurityContextRepository, unlike the standalone controller tests
// above.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrganizationMeSecurityChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrganizationRepository organizationRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedMeReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        "Your organization session is not active. Please sign in again."
                ));
    }

    @Test
    void organizationSessionReturnsSafeCurrentOrganization()
            throws Exception {

        when(organizationRepository.existsByIdAndStatus(
                7L, "APPROVED")).thenReturn(true);

        Authentication authentication =
                OrganizationLoginAuthenticationToken.authenticated(
                        new OrganizationPrincipal(
                                7L,
                                "Acme",
                                "acme@example.com"
                        )
                );

        mockMvc.perform(
                get("/api/v1/organizations/me")
                        .session(sessionFor(authentication))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.email").value("acme@example.com"))
                .andExpect(jsonPath("$.role").value("ORGANIZATION_ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void platformAdminSessionReturnsForbidden() throws Exception {
        Authentication platformAdmin =
                UsernamePasswordAuthenticationToken.authenticated(
                        "admin@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                );

        mockMvc.perform(
                get("/api/v1/organizations/me")
                        .session(sessionFor(platformAdmin))
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "This account is not authorized for the organization workspace."
                ));
    }

    @Test
    void otherAuthenticatedPrincipalReturnsForbidden() throws Exception {
        Authentication otherPrincipal =
                UsernamePasswordAuthenticationToken.authenticated(
                        "someone@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        mockMvc.perform(
                get("/api/v1/organizations/me")
                        .session(sessionFor(otherPrincipal))
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "This account is not authorized for the organization workspace."
                ));
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
}

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrganizationLogoutSecurityChainTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String SESSION_FAILURE_MESSAGE =
            "Your organization session is not active. Please sign in again.";
    private static final String WRONG_PRINCIPAL_MESSAGE =
            "This account is not authorized for the organization workspace.";

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedLogoutWithValidCsrfReturnsUnauthorized()
            throws Exception {

        CsrfTokens csrf = freshCsrfTokens();

        mockMvc.perform(
                post("/api/v1/organizations/logout")
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(SESSION_FAILURE_MESSAGE));
    }

    @Test
    void logoutWithoutCsrfTokenIsRejectedByRealCsrfFilter()
            throws Exception {

        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(
                post("/api/v1/organizations/logout")
                        .session(session)
        ).andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void logoutWithInvalidCsrfTokenIsRejectedByRealCsrfFilter()
            throws Exception {

        CsrfTokens csrf = freshCsrfTokens();
        MockHttpSession session = sessionFor(organizationAuthentication());

        mockMvc.perform(
                post("/api/v1/organizations/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, "invalid-token")
        ).andExpect(status().isForbidden());

        assertThat(session.isInvalid()).isFalse();
    }

    @Test
    void successfulLogoutInvalidatesSessionExpiresCsrfCookieAndBlocksOldSessionForMe()
            throws Exception {

        CsrfTokens csrf = freshCsrfTokens();
        MockHttpSession session = sessionFor(organizationAuthentication());
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

        mockMvc.perform(
                get("/api/v1/organizations/me")
                        .session(replayedSession)
        )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(SESSION_FAILURE_MESSAGE));
    }

    @Test
    void platformAdminSessionSurvivesDeniedOrganizationLogout()
            throws Exception {

        CsrfTokens csrf = freshCsrfTokens();
        MockHttpSession session = sessionFor(platformAdminAuthentication());

        MvcResult logoutResult = mockMvc.perform(
                post("/api/v1/organizations/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(WRONG_PRINCIPAL_MESSAGE))
                .andReturn();

        assertThat(logoutResult.getRequest().getSession(false))
                .isSameAs(session);
        assertThat(session.isInvalid()).isFalse();

        mockMvc.perform(
                get("/api/v1/platform-admin/organizations/counts")
                        .session(session)
        ).andExpect(status().isOk());
    }

    private CsrfTokens freshCsrfTokens() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);

        assertThat(cookie).isNotNull();

        return new CsrfTokens(cookie, cookie.getValue());
    }

    private static Authentication organizationAuthentication() {
        return OrganizationLoginAuthenticationToken.authenticated(
                new OrganizationPrincipal(
                        7L,
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
}
