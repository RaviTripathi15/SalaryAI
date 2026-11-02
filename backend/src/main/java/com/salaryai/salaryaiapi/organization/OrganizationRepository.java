
package com.salaryai.salaryaiapi.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    boolean existsByEmail(String email);

    Optional<Organization> findByEmail(String email);

    List<Organization> findByStatus(String status);

    @Query("SELECT o.status AS status, COUNT(o) AS total " +
           "FROM Organization o GROUP BY o.status")
    List<Map<String, Object>> countOrganizationsByStatus();
}
