package com.salaryai.salaryaiapi.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Organization-scoped holiday calendar access. */
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<Holiday> findByOrganizationIdAndHolidayDate(Long organizationId, LocalDate holidayDate);

    List<Holiday> findAllByOrganizationIdOrderByHolidayDateAsc(Long organizationId);

    boolean existsByOrganizationIdAndHolidayDateAndIdNot(
            Long organizationId, LocalDate holidayDate, Long id);

    @Query(value = """
            SELECT h FROM Holiday h
            WHERE h.organization.id = :organizationId
              AND (:fromDate IS NULL OR h.holidayDate >= :fromDate)
              AND (:toDate IS NULL OR h.holidayDate <= :toDate)
            """, countQuery = """
            SELECT COUNT(h) FROM Holiday h
            WHERE h.organization.id = :organizationId
              AND (:fromDate IS NULL OR h.holidayDate >= :fromDate)
              AND (:toDate IS NULL OR h.holidayDate <= :toDate)
            """)
    Page<Holiday> findByOrganizationIdWithFilters(
            @Param("organizationId") Long organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}
