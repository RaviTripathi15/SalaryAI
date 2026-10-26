
package com.salaryai.salaryaiapi.organization;

import com.salaryai.salaryaiapi.organization.dto.OrganizationRegistrationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(
            OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerOrganization(
            @Valid @RequestBody OrganizationRegistrationRequest request) {

        organizationService.registerOrganization(
                request.name(),
                request.email(),
                request.phone(),
                request.password()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Organization registration submitted successfully. Awaiting approval.");
    }
}
