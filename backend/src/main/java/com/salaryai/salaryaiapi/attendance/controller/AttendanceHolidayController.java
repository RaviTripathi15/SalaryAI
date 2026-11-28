package com.salaryai.salaryaiapi.attendance.controller;

import com.salaryai.salaryaiapi.attendance.AttendanceRequestContext;
import com.salaryai.salaryaiapi.attendance.HolidayService;
import com.salaryai.salaryaiapi.attendance.dto.HolidayRequest;
import com.salaryai.salaryaiapi.attendance.dto.HolidayResponse;
import com.salaryai.salaryaiapi.attendance.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/organizations/attendance/holidays")
public class AttendanceHolidayController {

    private static final String PATH = "/api/v1/organizations/attendance/holidays";

    private final HolidayService holidayService;

    public AttendanceHolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @PostMapping
    public ResponseEntity<HolidayResponse> create(
            @Valid @RequestBody HolidayRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        HolidayResponse created = holidayService.create(
                organizationId, AttendanceRequestContext.actor(authentication), request);
        return ResponseEntity.created(URI.create(PATH + "/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<HolidayResponse> list(
            @RequestParam(name = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) LocalDate toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return holidayService.list(organizationId, fromDate, toDate, page, size);
    }

    @PutMapping("/{id}")
    public HolidayResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody HolidayRequest request,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        return holidayService.update(
                organizationId, id, AttendanceRequestContext.actor(authentication), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id,
            @RequestParam(name = "version") Long version,
            Authentication authentication) {

        Long organizationId = AttendanceRequestContext.organizationId(authentication);
        holidayService.delete(
                organizationId, id, AttendanceRequestContext.actor(authentication), version);
        return ResponseEntity.noContent().build();
    }
}
