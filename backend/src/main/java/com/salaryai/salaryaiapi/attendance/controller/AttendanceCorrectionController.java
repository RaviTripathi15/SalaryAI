package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceCorrectionService;
import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.dto.CorrectionRequest;
import com.salaryai.salaryaiapi.attendance.dto.CorrectionResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/organizations/attendance/corrections")
public class AttendanceCorrectionController {

    private static final String PATH = "/api/v1/organizations/attendance/corrections";

    private final AttendanceCorrectionService correctionService;

    public AttendanceCorrectionController(AttendanceCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @PostMapping
    public ResponseEntity<CorrectionResponse> correct(
            @Valid @RequestBody CorrectionRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        CorrectionResponse created = correctionService.correct(
                organizationId, AttendanceRequestContext.actor(authentication), request);
        return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<CorrectionResponse> list(
            @RequestParam(name = "dailyAttendanceId", required = false) Long dailyAttendanceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return correctionService.list(organizationId, dailyAttendanceId, page, size);
    }
}
