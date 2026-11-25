
package com.salaryai.salaryaiapi.organization;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class OrganizationPrincipal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ROLE_ORGANIZATION_ADMIN =
            "ROLE_ORGANIZATION_ADMIN";

    private final Long id;
    private final String name;
    private final String email;
    private final String role;

    public OrganizationPrincipal(
            Long id,
            String name,
            String email) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.role = ROLE_ORGANIZATION_ADMIN;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OrganizationPrincipal that)) {
            return false;
        }

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OrganizationPrincipal{id=" + id
                + ", name='" + name + '\''
                + ", email='" + email + '\''
                + '}';
    }
}
