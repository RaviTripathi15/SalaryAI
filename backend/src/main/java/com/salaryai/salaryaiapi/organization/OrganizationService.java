
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

    @Transactional(readOnly = true)
public java.util.List<Organization> getPendingOrganizations() {
    return organizationRepository.findByStatus("PENDING");
}

@Transactional
public Organization updateOrganizationStatus(Long id, String status) {
    Organization organization = organizationRepository.findById(id)
            .orElseThrow(() ->
                    new IllegalArgumentException("Organization not found."));

    if (!organization.getStatus().equals("PENDING")) {
        throw new IllegalStateException(
                "Only pending organizations can be reviewed.");
    }

    if (!status.equals("APPROVED") && !status.equals("REJECTED")) {
        throw new IllegalArgumentException("Invalid organization status.");
    }

    organization.setStatus(status);
    return organizationRepository.save(organization);
}
}
