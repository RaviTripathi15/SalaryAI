
package com.salaryai.salaryaiapi.admin;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminService {

    private static final long SETUP_LOCK_KEY = 738291L;

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminService(
            PlatformAdminRepository platformAdminRepository,
            PasswordEncoder passwordEncoder) {

        this.platformAdminRepository = platformAdminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean adminAlreadyExists() {
        return platformAdminRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public boolean emailAlreadyExists(String email) {
        return platformAdminRepository.existsByEmail(email);
    }

    @Transactional
    public PlatformAdmin createInitialAdmin(
            String email,
            String rawPassword) {

        platformAdminRepository.acquireSetupLock(SETUP_LOCK_KEY);

        if (adminAlreadyExists()) {
            throw new IllegalStateException(
                    "Platform Administrator setup has already been completed.");
        }

        if (emailAlreadyExists(email)) {
            throw new IllegalArgumentException(
                    "A Platform Administrator with this email already exists.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        PlatformAdmin admin = new PlatformAdmin(
                email,
                passwordHash);

        return platformAdminRepository.save(admin);
    }
}
