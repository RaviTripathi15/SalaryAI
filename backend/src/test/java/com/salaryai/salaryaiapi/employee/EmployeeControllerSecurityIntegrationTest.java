package com.salaryai.salaryaiapi.employee;

import com.jayway.jsonpath.JsonPath;
import com.salaryai.salaryaiapi.organization.Organization;
import com.salaryai.salaryaiapi.organization.OrganizationLoginAuthenticationToken;
import com.salaryai.salaryaiapi.organization.OrganizationPrincipal;
import com.salaryai.salaryaiapi.organization.OrganizationRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class EmployeeControllerSecurityIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final String EMPLOYEE_PATH = "/api/v1/organizations/employees";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Organization firstOrganization;
    private Organization secondOrganization;
    private Employee firstEmployee;
    private Employee secondEmployee;

    @BeforeEach
    void setUp() {
        firstOrganization = persistOrganization("employee-api-first");
        secondOrganization = persistOrganization("employee-api-second");
        firstEmployee = persistEmployee(
                firstOrganization,
                "EMP-A1",
                "api.first@example.com",
                "Ada");
        secondEmployee = persistEmployee(
                secondOrganization,
                "EMP-B1",
                "api.second@example.com",
                "Grace");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedListIsDenied() throws Exception {
        mockMvc.perform(get(EMPLOYEE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(
                        "Your organization session is not active. Please sign in again."));
    }

    @Test
    void platformAdminSessionIsDenied() throws Exception {
        mockMvc.perform(get(EMPLOYEE_PATH).session(adminSession()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "This account is not authorized for the organization workspace."));
    }

    @Test
    void createWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post(EMPLOYEE_PATH)
                        .session(sessionFor(firstOrganization))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(
                                secondOrganization.getId(),
                                "EMP-NEW",
                                "new.employee@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createIgnoresClientSuppliedOrganizationIdAndReturnsDto() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();

        MvcResult result = mockMvc.perform(post(EMPLOYEE_PATH)
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(
                                secondOrganization.getId(),
                                "emp-new",
                                "New.Employee@Example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeCode").value("EMP-NEW"))
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.organization").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        Long createdId = idFrom(result);
        assertThat(employeeRepository.findByOrganizationIdAndId(
                firstOrganization.getId(), createdId)).isPresent();
        assertThat(employeeRepository.findByOrganizationIdAndId(
                secondOrganization.getId(), createdId)).isEmpty();
    }

    @Test
    void createRejectsDuplicateWithStableConflictMessage() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();

        mockMvc.perform(post(EMPLOYEE_PATH)
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(
                                firstOrganization.getId(),
                                "EMP-A1",
                                "another.employee@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "An employee with these details already exists in this organization."));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();
        String payload = """
                {
                  "employeeCode": "EMP-INVALID",
                  "firstName": "",
                  "lastName": "Lovelace",
                  "workEmail": "invalid@example.com",
                  "joiningDate": "2024-01-15"
                }
                """;

        MvcResult result = mockMvc.perform(post(EMPLOYEE_PATH)
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        Object validationError = JsonPath.read(
                result.getResponse().getContentAsString(), "$.error");
        assertThat(validationError).isNotNull();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception", "SQL", "constraint");
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaTypeNotInternalError() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();

        mockMvc.perform(post(EMPLOYEE_PATH)
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void listIsTenantScopedWithPaginationAndFiltering() throws Exception {
        Employee secondFirstOrgEmployee = persistEmployee(
                firstOrganization,
                "EMP-A2",
                "api.first.second@example.com",
                "Alan");
        secondFirstOrgEmployee.setDepartment("Research");
        employeeRepository.saveAndFlush(secondFirstOrgEmployee);
        firstEmployee.setDepartment("Engineering");
        employeeRepository.saveAndFlush(firstEmployee);

        mockMvc.perform(get(EMPLOYEE_PATH)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "employeeCode")
                        .param("sortDirection", "asc")
                        .param("search", "engineering")
                        .param("status", "ACTIVE")
                        .session(sessionFor(firstOrganization)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].employeeCode").value("EMP-A1"))
                .andExpect(jsonPath("$.content[0].workEmail").value("api.first@example.com"));
    }

    @Test
    void listRejectsUnsafeSortFieldAndOversizedPage() throws Exception {
        mockMvc.perform(get(EMPLOYEE_PATH)
                        .param("sortBy", "passwordHash")
                        .session(sessionFor(firstOrganization)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid sort field."));

        mockMvc.perform(get(EMPLOYEE_PATH)
                        .param("size", "101")
                        .session(sessionFor(firstOrganization)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Page size must be between 1 and 100."));
    }

    @Test
    void getOtherTenantEmployeeReturnsGenericNotFound() throws Exception {
        MvcResult result = mockMvc.perform(
                        get(EMPLOYEE_PATH + "/" + secondEmployee.getId())
                                .session(sessionFor(firstOrganization)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"error\":\"Employee not found.\"}")
                .doesNotContain("EMP-B1", "api.second@example.com");
    }

    @Test
    void updateNormalizesMutableFieldsAndIgnoresClientSuppliedImmutableFields()
            throws Exception {

        CsrfTokens csrf = freshCsrfTokens();
        String payload = """
                {
                  "employeeCode": " emp-a1-updated ",
                  "firstName": "  Augusta  ",
                  "lastName": " Lovelace ",
                  "workEmail": " Augusta@Example.COM ",
                  "phone": "  555-0199  ",
                  "department": "  Analytics  ",
                  "designation": "  Analyst  ",
                  "joiningDate": "2024-02-01",
                  "version": %d,
                  "status": "INACTIVE",
                  "organizationId": %d
                }
                """.formatted(firstEmployee.getVersion(), secondOrganization.getId());

        mockMvc.perform(put(EMPLOYEE_PATH + "/" + firstEmployee.getId())
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value("EMP-A1-UPDATED"))
                .andExpect(jsonPath("$.firstName").value("Augusta"))
                .andExpect(jsonPath("$.workEmail").value("augusta@example.com"))
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.organizationId").doesNotExist());

        Employee reloaded = employeeRepository.findByOrganizationIdAndId(
                firstOrganization.getId(), firstEmployee.getId()).orElseThrow();
        assertThat(reloaded.getOrganization().getId()).isEqualTo(firstOrganization.getId());
        assertThat(reloaded.getEmploymentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(reloaded.getDeactivatedAt()).isNull();
        assertThat(reloaded.getJoiningDate()).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void updateOtherTenantEmployeeReturnsNotFound() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();
        String payload = updateJson(secondEmployee.getVersion(), "EMP-X", "x@example.com");

        MvcResult result = mockMvc.perform(put(EMPLOYEE_PATH + "/" + secondEmployee.getId())
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("{\"error\":\"Employee not found.\"}");
    }

    @Test
    void updateWithStaleVersionReturnsConflict() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();
        String payload = updateJson(999L, "EMP-A1", "api.first@example.com");

        mockMvc.perform(put(EMPLOYEE_PATH + "/" + firstEmployee.getId())
                        .session(sessionFor(firstOrganization))
                        .cookie(csrf.cookie())
                        .header(CSRF_HEADER, csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "The employee record changed. Reload it and try again."));
    }

    @Test
    void deactivateIsIdempotentForOwnEmployeeAndTenantScoped() throws Exception {
        CsrfTokens first = freshCsrfTokens();
        MvcResult firstResult = mockMvc.perform(post(
                        EMPLOYEE_PATH + "/" + firstEmployee.getId() + "/deactivate")
                        .session(sessionFor(firstOrganization))
                        .cookie(first.cookie())
                        .header(CSRF_HEADER, first.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.deactivatedAt").isNotEmpty())
                .andReturn();

        Number firstVersion = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.version");

        CsrfTokens second = freshCsrfTokens();
        mockMvc.perform(post(EMPLOYEE_PATH + "/" + firstEmployee.getId() + "/deactivate")
                        .session(sessionFor(firstOrganization))
                        .cookie(second.cookie())
                        .header(CSRF_HEADER, second.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(firstVersion.longValue()));

        CsrfTokens crossTenant = freshCsrfTokens();
        mockMvc.perform(post(EMPLOYEE_PATH + "/" + secondEmployee.getId() + "/deactivate")
                        .session(sessionFor(firstOrganization))
                        .cookie(crossTenant.cookie())
                        .header(CSRF_HEADER, crossTenant.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Employee not found."));
    }

    @Test
    void approvalDowngradeInExistingSessionIsDeniedImmediately() throws Exception {
        mockMvc.perform(get(EMPLOYEE_PATH).session(sessionFor(firstOrganization)))
                .andExpect(status().isOk());

        firstOrganization.setStatus("PENDING");
        organizationRepository.saveAndFlush(firstOrganization);

        mockMvc.perform(get(EMPLOYEE_PATH).session(sessionFor(firstOrganization)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "This account is not authorized for the organization workspace."));
    }

    @Test
    void noDeleteEndpointIsExposed() throws Exception {
        CsrfTokens csrf = freshCsrfTokens();

        MvcResult result = mockMvc.perform(
                        delete(EMPLOYEE_PATH + "/" + firstEmployee.getId())
                                .session(sessionFor(firstOrganization))
                                .cookie(csrf.cookie())
                                .header(CSRF_HEADER, csrf.token()))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        assertThat(result.getResponse().getHeader("Allow")).contains("GET");
    }

    private CsrfTokens freshCsrfTokens() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return new CsrfTokens(cookie, cookie.getValue());
    }

    private static MockHttpSession sessionFor(Organization organization) {
        OrganizationPrincipal principal = new OrganizationPrincipal(
                organization.getId(),
                organization.getName(),
                organization.getEmail());
        Authentication authentication =
                OrganizationLoginAuthenticationToken.authenticated(principal);
        return sessionFor(authentication);
    }

    private static MockHttpSession adminSession() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        return sessionFor(authentication);
    }

    private static MockHttpSession sessionFor(Authentication authentication) {
        MockHttpSession session = new MockHttpSession();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        return session;
    }

    private Organization persistOrganization(String suffix) {
        Organization organization = new Organization(
                "Employee API " + suffix,
                suffix + ".org@example.com",
                "5550100",
                "test-hash");
        organization.setStatus("APPROVED");
        return organizationRepository.saveAndFlush(organization);
    }

    private Employee persistEmployee(
            Organization organization,
            String employeeCode,
            String workEmail,
            String firstName) {

        Employee employee = new Employee(
                organization,
                employeeCode,
                firstName,
                "Tester",
                workEmail,
                LocalDate.of(2024, 1, 15));
        employee.setDepartment("Engineering");
        return employeeRepository.saveAndFlush(employee);
    }

    private static String createJson(Long organizationId, String employeeCode, String workEmail) {
        return """
                {
                  "organizationId": %d,
                  "status": "INACTIVE",
                  "employeeCode": "%s",
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "workEmail": "%s",
                  "phone": "555-0100",
                  "department": "Engineering",
                  "designation": "Engineer",
                  "joiningDate": "2024-01-15"
                }
                """.formatted(organizationId, employeeCode, workEmail);
    }

    private static String updateJson(Long version, String employeeCode, String workEmail) {
        return """
                {
                  "employeeCode": "%s",
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "workEmail": "%s",
                  "phone": "555-0100",
                  "department": "Engineering",
                  "designation": "Engineer",
                  "joiningDate": "2024-01-15",
                  "version": %d
                }
                """.formatted(employeeCode, workEmail, version);
    }

    private static Long idFrom(MvcResult result) throws Exception {
        Object value = JsonPath.read(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.id");
        return ((Number) value).longValue();
    }

    private record CsrfTokens(Cookie cookie, String token) {}
}
