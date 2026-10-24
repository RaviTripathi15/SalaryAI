
package com.salaryai.salaryaiapi.organization;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean emailAlreadyExists(String email) {
        return organizationRepository.existsByEmail(email);
    }

    @Transactional
    public Organization registerOrganization(
            String name,
            String email,
            String phone,
            String rawPassword) {

        if (organizationRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "An organization with this email already exists.");
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);

        Organization organization = new Organization(
                name,
                email,
                phone,
                hashedPassword);

        return organizationRepository.save(organization);
    }
}
