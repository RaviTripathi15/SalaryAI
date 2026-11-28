package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Organization-scoped schedule access. */
public interface AttendanceScheduleRepository extends JpaRepository<AttendanceSchedule, Long> {

    Optional<AttendanceSchedule> findByIdAndOrganizationId(Long id, Long organizationId);

    List<AttendanceSchedule> findAllByOrganizationIdOrderByEffectiveFromDesc(Long organizationId);

    boolean existsByNameAndOrganizationIdAndIdNot(Long organizationId, String name, Long id);
}
