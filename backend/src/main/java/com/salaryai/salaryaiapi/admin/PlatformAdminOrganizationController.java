
package com.salaryai.salaryaiapi.admin;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
import com.salaryai.salaryaiapi.admin.dto.OrganizationReviewResponse;
import com.salaryai.salaryaiapi.organization.Organization;
import com.salaryai.salaryaiapi.organization.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform-admin/organizations")
public class PlatformAdminOrganizationController {

    private final OrganizationService organizationService;

    public PlatformAdminOrganizationController(
            OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/counts")
public ResponseEntity<Map<String, Long>> getOrganizationStatusCounts() {
    return ResponseEntity.ok(
            organizationService.getOrganizationStatusCounts()
    );
}

@GetMapping
public ResponseEntity<List<OrganizationReviewResponse>> getOrganizationsByStatus(
        @RequestParam String status) {

    List<OrganizationReviewResponse> organizations =
            organizationService.getOrganizationsByStatus(status)
                    .stream()
                    .map(this::toResponse)
                    .toList();

    return ResponseEntity.ok(organizations);
}

    @GetMapping("/pending")
    public ResponseEntity<List<OrganizationReviewResponse>>
            getPendingOrganizations() {

        List<OrganizationReviewResponse> organizations =
                organizationService.getPendingOrganizations()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(organizations);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<OrganizationReviewResponse>
            approveOrganization(@PathVariable Long id) {

        Organization organization =
                organizationService.updateOrganizationStatus(
                        id, "APPROVED");

        return ResponseEntity.ok(toResponse(organization));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<OrganizationReviewResponse>
            rejectOrganization(@PathVariable Long id) {

        Organization organization =
                organizationService.updateOrganizationStatus(
                        id, "REJECTED");

        return ResponseEntity.ok(toResponse(organization));
    }

    private OrganizationReviewResponse toResponse(
            Organization organization) {

        return new OrganizationReviewResponse(
                organization.getId(),
                organization.getName(),
                organization.getEmail(),
                organization.getPhone(),
                organization.getStatus(),
                organization.getCreatedAt()
        );
    }
}
