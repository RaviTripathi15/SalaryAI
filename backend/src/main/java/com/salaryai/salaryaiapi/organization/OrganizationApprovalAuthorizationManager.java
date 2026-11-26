package com.salaryai.salaryaiapi.organization;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class OrganizationApprovalAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    OrganizationApprovalAuthorizationManager.class);

    private static final String APPROVED_STATUS = "APPROVED";

    private static final AuthenticationTrustResolver TRUST_RESOLVER =
            new AuthenticationTrustResolverImpl();

    private final OrganizationRepository organizationRepository;

    public OrganizationApprovalAuthorizationManager(
            OrganizationRepository organizationRepository) {

        this.organizationRepository = organizationRepository;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {

        Authentication authentication;

        try {
            authentication = authenticationSupplier.get();
        } catch (RuntimeException exception) {
            LOG.warn("Unable to resolve authentication for organization check",
                    exception);
            return deny();
        }

        if (authentication == null
                || !authentication.isAuthenticated()
                || TRUST_RESOLVER.isAnonymous(authentication)) {

            return deny();
        }

        if (!(authentication.getPrincipal()
                instanceof OrganizationPrincipal principal)) {

            return deny();
        }

        boolean hasOrganizationRole = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(OrganizationPrincipal.ROLE_ORGANIZATION_ADMIN::equals);

        if (!hasOrganizationRole) {
            return deny();
        }

        Long organizationId = principal.getId();

        if (organizationId == null) {
            return deny();
        }

        try {
            boolean approved = organizationRepository.existsByIdAndStatus(
                    organizationId,
                    APPROVED_STATUS
            );

            if (approved) {
                return new AuthorizationDecision(true);
            }
        } catch (Exception exception) {
            LOG.warn(
                    "Organization approval check failed for organization {}",
                    organizationId,
                    exception
            );
        }

        return deny();
    }

    private static AuthorizationResult deny() {
        return new AuthorizationDecision(false);
    }
}
