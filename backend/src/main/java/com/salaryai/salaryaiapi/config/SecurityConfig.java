
package com.salaryai.salaryaiapi.config;

import com.salaryai.salaryaiapi.admin.PlatformAdminUserDetailsService;
import com.salaryai.salaryaiapi.exception.OrganizationAccessDeniedHandler;
import com.salaryai.salaryaiapi.exception.OrganizationAuthenticationEntryPoint;
import com.salaryai.salaryaiapi.organization.OrganizationApprovalAuthorizationManager;
import com.salaryai.salaryaiapi.organization.OrganizationAuthenticationProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            OrganizationApprovalAuthorizationManager
                    organizationApprovalAuthorizationManager,
            OrganizationAuthenticationEntryPoint
                    organizationAuthenticationEntryPoint,
            OrganizationAccessDeniedHandler
                    organizationAccessDeniedHandler)
            throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(
                    new CsrfTokenRequestAttributeHandler()
                )
            )

            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true)
            )

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/organizations/register",
                    "/api/v1/organizations/login",
                    "/api/v1/organizations/logout",
                    "/api/v1/platform-admin/login"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/csrf"
                ).permitAll()

                .requestMatchers("/api/v1/platform-admin/**")
                .hasRole("PLATFORM_ADMIN")

                .requestMatchers("/api/v1/organizations/**")
                .access(organizationApprovalAuthorizationManager)

                .anyRequest().authenticated()
            )

            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    organizationAuthenticationEntryPoint,
                    PathPatternRequestMatcher.pathPattern(
                        "/api/v1/organizations/**"
                    )
                )
                .defaultAccessDeniedHandlerFor(
                    organizationAccessDeniedHandler,
                    PathPatternRequestMatcher.pathPattern(
                        "/api/v1/organizations/**"
                    )
                )
            )

            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SessionAuthenticationStrategy organizationSessionAuthenticationStrategy() {
        ChangeSessionIdAuthenticationStrategy strategy =
                new ChangeSessionIdAuthenticationStrategy();

        strategy.setAlwaysCreateSession(true);

        return strategy;
    }

    @Bean
    public AuthenticationManager platformAdminAuthenticationManager(
            PlatformAdminUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(List.of(provider));
    }

    @Bean
    public AuthenticationManager organizationAuthenticationManager(
            OrganizationAuthenticationProvider organizationProvider) {

        return new ProviderManager(
            List.of(organizationProvider)
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
