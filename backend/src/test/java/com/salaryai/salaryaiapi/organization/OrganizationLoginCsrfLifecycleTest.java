package com.salaryai.salaryaiapi.organization;

import com.salaryai.salaryaiapi.config.CsrfController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.method.annotation.CsrfTokenArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationLoginCsrfLifecycleTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Mock
    private OrganizationAuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        CookieCsrfTokenRepository csrfTokenRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();

        CsrfFilter csrfFilter = new CsrfFilter(csrfTokenRepository);
        csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());

        ChangeSessionIdAuthenticationStrategy sessionStrategy =
                new ChangeSessionIdAuthenticationStrategy();

        sessionStrategy.setAlwaysCreateSession(true);

        Authentication authenticated = authenticatedToken();

        lenient().when(authenticationService.authenticate(
                any(), any(), any())).thenReturn(authenticated);

        OrganizationAuthController controller =
                new OrganizationAuthController(
                        authenticationService,
                        sessionStrategy,
                        new HttpSessionSecurityContextRepository(),
                        csrfTokenRepository
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CsrfController(), controller)
                .setCustomArgumentResolvers(new CsrfTokenArgumentResolver())
                .addFilters(csrfFilter)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginExpiresOldCsrfTokenAndFreshTokenIsRequiredAfterwards()
            throws Exception {

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie oldCookie = csrfResult.getResponse().getCookie(CSRF_COOKIE);

        assertThat(oldCookie).isNotNull();

        String oldToken = oldCookie.getValue();

        mockMvc.perform(get("/api/v1/csrf").cookie(oldCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(oldToken));

        MvcResult loginResult = mockMvc.perform(
                post("/api/v1/organizations/login")
                        .cookie(oldCookie)
                        .header(CSRF_HEADER, oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Acme",
                                  "email": "acme@example.com",
                                  "password": "secret"
                                }
                                """)
        )
                .andExpect(status().isOk())
                .andReturn();

        Cookie deletedCookie =
                loginResult.getResponse().getCookie(CSRF_COOKIE);

        assertThat(deletedCookie).isNotNull();
        assertThat(deletedCookie.getValue()).isEmpty();
        assertThat(deletedCookie.getMaxAge()).isZero();

        MvcResult freshCsrfResult = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie freshCookie =
                freshCsrfResult.getResponse().getCookie(CSRF_COOKIE);

        assertThat(freshCookie).isNotNull();

        String freshToken = freshCookie.getValue();

        assertThat(freshToken).isNotBlank().isNotEqualTo(oldToken);

        mockMvc.perform(
                post("/api/v1/organizations/login")
                        .cookie(freshCookie)
                        .header(CSRF_HEADER, oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Acme",
                                  "email": "acme@example.com",
                                  "password": "secret"
                                }
                                """)
        ).andExpect(status().isForbidden());
    }

    @Test
    void loginWithoutCsrfTokenIsRejectedByCsrfFilter() throws Exception {
        mockMvc.perform(
                post("/api/v1/organizations/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Acme",
                                  "email": "acme@example.com",
                                  "password": "secret"
                                }
                                """)
        ).andExpect(status().isForbidden());
    }

    private static Authentication authenticatedToken() {
        OrganizationPrincipal principal =
                new OrganizationPrincipal(7L, "Acme", "acme@example.com");

        return OrganizationLoginAuthenticationToken.authenticated(principal);
    }
}
