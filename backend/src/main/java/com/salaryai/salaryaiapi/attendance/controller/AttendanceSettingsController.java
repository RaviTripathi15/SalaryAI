package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceConfigurationService;
import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.dto.AttendanceSettingsRequest;
import com.salaryai.salaryaiapi.attendance.dto.AttendanceSettingsResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/attendance/settings")
public class AttendanceSettingsController {

    private final AttendanceConfigurationService configurationService;

    public AttendanceSettingsController(AttendanceConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public AttendanceSettingsResponse get(Authentication authentication) {
        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.getSettings(organizationId);
    }

    @PutMapping
    public AttendanceSettingsResponse save(
            @Valid @RequestBody AttendanceSettingsRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return configurationService.saveSettings(
                organizationId, AttendanceRequestContext.actor(authentication), request);
    }
}
