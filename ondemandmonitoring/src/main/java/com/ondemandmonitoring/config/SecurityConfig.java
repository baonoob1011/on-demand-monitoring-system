package com.ondemandmonitoring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.ondemandmonitoring.user.enumeration.UserRole;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> BUSINESS_ROLE_GROUPS = Set.of(
            UserRole.CUSTOMER.name(),
            UserRole.STAFF.name(),
            UserRole.DRONE_OPERATOR.name(),
            UserRole.SYSTEM_OPERATOR.name(),
            UserRole.ADMIN.name());

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/first-login/change-password",
            "/api/v1/auth/social/sync",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/csrf",
            "/api/v1/auth/logout",
            "/v3/api-docs/**",
            // Simulation Viewer – static assets and the APIs called by viewer.js
            "/simulation-viewer",
            "/simulation-viewer/**",
            "/api/zones",
            "/api/zones/**",
            "/api/simulation-map",
            "/api/simulation-map/**",
            "/api/missions/*/images",
            "/api/missions/*/media",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/resend-otp",
                                "/api/v1/auth/login",
                                "/api/v1/auth/first-login/change-password",
                                "/api/v1/auth/social/sync",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                // Simulation Viewer APIs (PUT/POST/DELETE from browser JS)
                                "/api/zones/**",
                                "/api/simulation-map/**",
                                "/api/missions/*/images",
                                "/api/missions/*/media"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups == null) {
                return List.of();
            }
            return groups.stream()
                    .filter(BUSINESS_ROLE_GROUPS::contains)
                    .map(group -> new SimpleGrantedAuthority("ROLE_" + group))
                    .map(authority -> (GrantedAuthority) authority)
                    .toList();
        });
        return converter;
    }
}
