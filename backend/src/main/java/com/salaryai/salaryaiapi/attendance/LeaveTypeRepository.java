package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Organization-scoped leave type access. */
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByIdAndOrganizationId(Long id, Long organizationId);

    List<LeaveType> findAllByOrganizationIdOrderByNameAsc(Long organizationId);

    boolean existsByNameAndOrganizationIdAndIdNot(Long organizationId, String name, Long id);
}
