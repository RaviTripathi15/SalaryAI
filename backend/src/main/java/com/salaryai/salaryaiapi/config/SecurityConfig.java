package com.salaryai.salaryaiapi.config;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository)
            throws Exception {

        http
           .csrf(csrf -> csrf
    .csrfTokenRepository(
        CookieCsrfTokenRepository.withHttpOnlyFalse()
    )
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
        "/api/v1/organizations/register",
        "/api/v1/platform-admin/login",
        "/api/v1/csrf"
    ).permitAll()

    .requestMatchers("/api/v1/platform-admin/**")
    .hasRole("PLATFORM_ADMIN")

    .anyRequest().authenticated()
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
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}