package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Organization-scoped settings access. Unscoped inherited methods must not be
 * used by attendance business logic.
 */
public interface AttendanceSettingsRepository extends JpaRepository<AttendanceSettings, Long> {

    Optional<AttendanceSettings> findByOrganizationId(Long organizationId);
}
