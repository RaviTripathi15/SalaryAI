package com.salaryai.salaryaiapi.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(
                organizationRepository,
                passwordEncoder
        );
    }

    @Test
    void registrationNormalizesNameAndEmail() {
        when(organizationRepository.existsByEmailIgnoreCase("acme@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Organization saved = organizationService.registerOrganization(
                "  Acme Corp  ",
                "  ACME@Example.COM  ",
                "5550100",
                "secret"
        );

        assertThat(saved.getName()).isEqualTo("Acme Corp");
        assertThat(saved.getEmail()).isEqualTo("acme@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void caseInsensitiveDuplicateEmailRegistrationIsRejected() {
        when(organizationRepository.existsByEmailIgnoreCase("acme@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> organizationService.registerOrganization(
                "Acme Corp",
                "ACME@Example.COM",
                "5550100",
                "secret"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An organization with this email already exists.");

        verify(organizationRepository, never()).save(any(Organization.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void emailAlreadyExistsUsesNormalizedIgnoreCaseLookup() {
        when(organizationRepository.existsByEmailIgnoreCase("acme@example.com"))
                .thenReturn(true);

        assertThat(organizationService.emailAlreadyExists(
                "  ACME@Example.com ")).isTrue();

        verify(organizationRepository)
                .existsByEmailIgnoreCase("acme@example.com");
    }

    @Test
    void duplicateCheckArgumentIsNormalized() {
        when(organizationRepository.existsByEmailIgnoreCase(anyString()))
                .thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        organizationService.registerOrganization(
                "Acme",
                "  MiXeD@Example.COM ",
                "5550100",
                "secret"
        );

        ArgumentCaptor<Organization> captor =
                ArgumentCaptor.forClass(Organization.class);

        verify(organizationRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail())
                .isEqualTo("mixed@example.com");
    }
}
