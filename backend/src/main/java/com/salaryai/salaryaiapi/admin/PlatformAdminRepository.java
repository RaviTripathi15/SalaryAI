
package com.salaryai.salaryaiapi.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlatformAdminRepository
        extends JpaRepository<PlatformAdmin, Long> {

    boolean existsByEmail(String email);

    Optional<PlatformAdmin> findByEmail(String email);

    @Query(value = "SELECT pg_advisory_xact_lock(:lockKey)",
           nativeQuery = true)
    void acquireSetupLock(@Param("lockKey") long lockKey);
}
