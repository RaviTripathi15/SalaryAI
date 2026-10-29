
package com.salaryai.salaryaiapi.admin;

import com.salaryai.salaryaiapi.admin.dto.AdminSetupRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/setup")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final String setupSecret;

    public PlatformAdminController(
            PlatformAdminService platformAdminService,
            @Value("${salaryai.admin-setup-secret:}") String setupSecret) {
        this.platformAdminService = platformAdminService;
        this.setupSecret = setupSecret;
    }

    @PostMapping
    public ResponseEntity<String> setupInitialAdmin(
            @RequestHeader(
                    value = "X-Admin-Setup-Secret",
                    required = false) String providedSecret,
            @Valid @RequestBody AdminSetupRequest request) {

        if (setupSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Administrator setup is not configured.");
        }

        if (providedSecret == null
                || !java.security.MessageDigest.isEqual(
                        setupSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        providedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid setup secret.");
        }

        

        platformAdminService.createInitialAdmin(
                request.email(),
                request.password());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Platform Administrator created successfully.");
    }
}
