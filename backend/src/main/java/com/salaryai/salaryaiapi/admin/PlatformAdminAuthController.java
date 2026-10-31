package com.salaryai.salaryaiapi.admin;


import com.salaryai.salaryaiapi.admin.dto.AdminLoginRequest;
import com.salaryai.salaryaiapi.admin.dto.AdminLoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
public class PlatformAdminAuthController {

    private final PlatformAdminAuthenticationService authenticationService;
    private final PlatformAdminRepository platformAdminRepository;
    private final SecurityContextRepository securityContextRepository;

    public PlatformAdminAuthController(
            PlatformAdminAuthenticationService authenticationService,
            PlatformAdminRepository platformAdminRepository,
            SecurityContextRepository securityContextRepository) {

        this.authenticationService = authenticationService;
        this.platformAdminRepository = platformAdminRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {

        Authentication authentication =
                authenticationService.authenticate(
                        request.email(),
                        request.password()
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
        );

        PlatformAdmin admin = platformAdminRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        AdminLoginResponse response = new AdminLoginResponse(
                admin.getId(),
                admin.getEmail(),
                "PLATFORM_ADMIN"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
public ResponseEntity<AdminLoginResponse> getCurrentAdmin(
        Authentication authentication) {

    PlatformAdmin admin = platformAdminRepository
            .findByEmail(authentication.getName())
            .orElseThrow();

    return ResponseEntity.ok(
            new AdminLoginResponse(
                    admin.getId(),
                    admin.getEmail(),
                    "PLATFORM_ADMIN"
            )
    );
}

@PostMapping("/logout")
public ResponseEntity<String> logout(
        jakarta.servlet.http.HttpServletRequest request,
        jakarta.servlet.http.HttpServletResponse response) {

    new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
            .logout(
                    request,
                    response,
                    SecurityContextHolder.getContext().getAuthentication()
            );

    return ResponseEntity.ok("Logged out successfully.");
}
}