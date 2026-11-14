
package com.salaryai.salaryaiapi.organization;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAuthenticationProvider
        extends DaoAuthenticationProvider {

    public OrganizationAuthenticationProvider(
            OrganizationUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
    }
}
