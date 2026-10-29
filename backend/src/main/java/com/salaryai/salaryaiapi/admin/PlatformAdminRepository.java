
package com.salaryai.salaryaiapi.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAdminRepository
        extends JpaRepository<PlatformAdmin, Long> {

    boolean existsByEmail(String email);

    Optional<PlatformAdmin> findByEmail(String email);
}
