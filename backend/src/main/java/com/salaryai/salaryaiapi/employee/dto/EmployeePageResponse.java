package com.salaryai.salaryaiapi.employee.dto;

import java.util.List;

public record EmployeePageResponse(

        List<EmployeeResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last

) {}
