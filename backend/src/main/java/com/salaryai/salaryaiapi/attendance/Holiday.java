package com.salaryai.salaryaiapi.attendance;

import com.salaryai.salaryaiapi.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

@Entity
@Table(
        name = "holidays",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holidays_org_date",
                columnNames = {"organization_id", "holiday_date"}))
public class Holiday extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_holidays_organization"))
    private Organization organization;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "calendar_name", length = 100)
    private String calendarName;

    protected Holiday() {
        // Required by JPA.
    }

    public Holiday(
            Organization organization,
            String name,
            LocalDate holidayDate,
            String description,
            String calendarName) {

        this.organization = organization;
        this.name = name;
        this.holidayDate = holidayDate;
        this.description = description;
        this.calendarName = calendarName;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getDescription() {
        return description;
    }

    public String getCalendarName() {
        return calendarName;
    }

    public void update(String name, LocalDate holidayDate, String description, String calendarName) {
        this.name = name;
        this.holidayDate = holidayDate;
        this.description = description;
        this.calendarName = calendarName;
    }
}
