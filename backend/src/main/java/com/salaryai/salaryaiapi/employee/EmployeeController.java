package com.salaryai.salaryaiapi.employee;

import com.salaryai.salaryaiapi.employee.dto.EmployeeCreateRequest;
import com.salaryai.salaryaiapi.employee.dto.EmployeePageResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeResponse;
import com.salaryai.salaryaiapi.employee.dto.EmployeeUpdateRequest;
import com.salaryai.salaryaiapi.employee.exception.UnauthorizedEmployeeAccessException;
import com.salaryai.salaryaiapi.organization.OrganizationPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/organizations/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest request,
            Authentication authentication) {

        Long organizationId = organizationId(authentication);
        EmployeeResponse created = employeeService.create(organizationId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/organizations/employees/" + created.id()))
                .body(created);
    }

    @GetMapping
    public EmployeePageResponse list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "employeeCode") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            Authentication authentication) {

        return employeeService.list(
                organizationId(authentication),
                page,
                size,
                sortBy,
                sortDirection,
                search,
                status);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(
            @PathVariable("id") Long id,
            Authentication authentication) {

        return employeeService.get(organizationId(authentication), id);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody EmployeeUpdateRequest request,
            Authentication authentication) {

        return employeeService.update(organizationId(authentication), id, request);
    }

    @PostMapping("/{id}/deactivate")
    public EmployeeResponse deactivate(
            @PathVariable("id") Long id,
            Authentication authentication) {

        return employeeService.deactivate(organizationId(authentication), id);
    }

    private static Long organizationId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof OrganizationPrincipal principal)
                || principal.getId() == null) {
            throw new UnauthorizedEmployeeAccessException();
        }
        return principal.getId();
    }
}
