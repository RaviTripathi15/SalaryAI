
package com.salaryai.salaryaiapi.organization;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

@Transactional(readOnly = true)
public List<Organization> getOrganizationsByStatus(String status) {
    if (!List.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
        throw new IllegalArgumentException("Invalid organization status.");
    }

    return organizationRepository.findByStatus(status);
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

@Transactional(readOnly = true)
public Map<String, Long> getOrganizationStatusCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();

    // Start each status at zero, including statuses with no organizations.
    counts.put("PENDING", 0L);
    counts.put("APPROVED", 0L);
    counts.put("REJECTED", 0L);

    // Get the actual grouped counts from PostgreSQL through the repository.
    List<Map<String, Object>> results =
            organizationRepository.countOrganizationsByStatus();

    for (Map<String, Object> row : results) {
        String status = (String) row.get("status");
        long total = ((Number) row.get("total")).longValue();

        counts.put(status, total);
    }

    return counts;
}
}
