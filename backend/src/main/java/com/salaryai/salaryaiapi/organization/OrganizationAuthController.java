package com.salaryai.salaryaiapi.organization;

import com.salaryai.salaryaiapi.organization.dto.OrganizationLoginRequest;
import com.salaryai.salaryaiapi.organization.dto.OrganizationLoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationAuthController {

    private static final String GENERIC_FAILURE_MESSAGE =
            "Invalid organization name, email or password.";

    private static final String SESSION_FAILURE_MESSAGE =
            "Your organization session is not active. Please sign in again.";

    private static final String WRONG_PRINCIPAL_MESSAGE =
            "This account is not authorized for the organization workspace.";

    private static final AuthenticationTrustResolver TRUST_RESOLVER =
            new AuthenticationTrustResolverImpl();

    private final OrganizationAuthenticationService authenticationService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public OrganizationAuthController(
            OrganizationAuthenticationService authenticationService,
            @Qualifier("organizationSessionAuthenticationStrategy")
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {

        this.authenticationService = authenticationService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<OrganizationLoginResponse> login(
            @Valid @RequestBody OrganizationLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Authentication authentication = authenticationService.authenticate(
                request.organizationName(),
                request.email(),
                request.password()
        );

        if (!(authentication.getPrincipal()
                instanceof OrganizationPrincipal principal)) {

            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        sessionAuthenticationStrategy.onAuthentication(
                authentication,
                httpRequest,
                httpResponse
        );

        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        return ResponseEntity.ok(
                OrganizationLoginResponse.from(principal)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentOrganization(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || TRUST_RESOLVER.isAnonymous(authentication)) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", SESSION_FAILURE_MESSAGE));
        }

        if (!(authentication.getPrincipal()
                instanceof OrganizationPrincipal principal)) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", WRONG_PRINCIPAL_MESSAGE));
        }

        boolean hasOrganizationRole = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OrganizationPrincipal.ROLE_ORGANIZATION_ADMIN::equals);

        if (!hasOrganizationRole) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", WRONG_PRINCIPAL_MESSAGE));
        }

        return ResponseEntity.ok(
                OrganizationLoginResponse.from(principal)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || TRUST_RESOLVER.isAnonymous(authentication)) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", SESSION_FAILURE_MESSAGE));
        }

        if (!(authentication.getPrincipal()
                instanceof OrganizationPrincipal)) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", WRONG_PRINCIPAL_MESSAGE));
        }

        boolean hasOrganizationRole = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OrganizationPrincipal.ROLE_ORGANIZATION_ADMIN::equals);

        if (!hasOrganizationRole) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", WRONG_PRINCIPAL_MESSAGE));
        }

        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        SecurityContextLogoutHandler logoutHandler =
                new SecurityContextLogoutHandler();

        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        logoutHandler.logout(httpRequest, httpResponse, authentication);

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(
                Map.of("message", "Logged out successfully.")
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailure(
            AuthenticationException exception) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", GENERIC_FAILURE_MESSAGE));
    }
}
