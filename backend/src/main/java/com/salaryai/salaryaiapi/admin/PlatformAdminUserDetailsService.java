
package com.salaryai.salaryaiapi.admin;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminUserDetailsService
        implements UserDetailsService {

    private final PlatformAdminRepository platformAdminRepository;

    public PlatformAdminUserDetailsService(
            PlatformAdminRepository platformAdminRepository) {
        this.platformAdminRepository = platformAdminRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        PlatformAdmin admin = platformAdminRepository
                .findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid email or password."));

        return User.withUsername(admin.getEmail())
                .password(admin.getPasswordHash())
                .roles("PLATFORM_ADMIN")
                .build();
    }
}
