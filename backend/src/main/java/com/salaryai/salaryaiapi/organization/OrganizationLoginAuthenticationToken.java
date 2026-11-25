
package com.salaryai.salaryaiapi.organization;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.Assert;

public class OrganizationLoginAuthenticationToken
        extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String organizationName;
    private final String email;
    private String password;
    private final Object principal;

    private OrganizationLoginAuthenticationToken(
            String organizationName,
            String email,
            String password) {

        super(Collections.<GrantedAuthority>emptyList());
        this.organizationName = organizationName;
        this.email = email;
        this.password = password;
        this.principal = email;
        super.setAuthenticated(false);
    }

    private OrganizationLoginAuthenticationToken(
            OrganizationPrincipal principal) {

        super(List.of(new SimpleGrantedAuthority(principal.getRole())));
        this.organizationName = principal.getName();
        this.email = principal.getEmail();
        this.password = null;
        this.principal = principal;
        super.setAuthenticated(true);
    }

    public static OrganizationLoginAuthenticationToken unauthenticated(
            String organizationName,
            String email,
            String password) {

        return new OrganizationLoginAuthenticationToken(
                organizationName, email, password);
    }

    public static OrganizationLoginAuthenticationToken authenticated(
            OrganizationPrincipal principal) {

        return new OrganizationLoginAuthenticationToken(principal);
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return email;
    }

    @Override
    public void setAuthenticated(boolean authenticated)
            throws IllegalArgumentException {

        Assert.isTrue(
                !authenticated,
                "Cannot set this token to trusted - use the authenticated(...) factory method instead"
        );
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.password = null;
    }
}
