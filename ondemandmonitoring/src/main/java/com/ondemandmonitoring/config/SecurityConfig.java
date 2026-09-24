package com.ondemandmonitoring.config;

import com.ondemandmonitoring.role.domain.RoleCode;
import lombok.extern.slf4j.Slf4j;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private static final Set<String> BUSINESS_ROLE_GROUPS = Set.of(
            RoleCode.CUSTOMER.name(),
            RoleCode.STAFF.name(),
            RoleCode.DRONE_OPERATOR.name(),
            RoleCode.SYSTEM_OPERATOR.name(),
            RoleCode.ADMIN.name());

    @Value("${cors.address:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
    private String allowedOrigins;

    static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/verify-otp",
            "/api/auth/resend-otp",
            "/api/auth/login",
            "/api/auth/first-login/change-password",
            "/api/auth/social/sync",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/csrf",
            "/api/auth/logout",
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
            "/ws",
            "/ws/**",
            // Simulation Viewer – static assets and the APIs called by viewer.js
            "/simulation-viewer",
            "/simulation-viewer/**",
            "/api/zones",
            "/api/zones/**",
            "/api/thermal-sources",
            "/api/thermal-sources/**",
            "/api/simulation-map",
            "/api/simulation-map/**",
            "/api/planning/environment",
            "/api/planning/environment/**",
            "/api/internal/v1/drone-telemetry/*",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("cognitoAccessTokenDecoder") JwtDecoder cognitoAccessTokenDecoder,
            ActiveAccountFilter activeAccountFilter) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-otp",
                                "/api/auth/resend-otp",
                                "/api/auth/login",
                                "/ws",
                                "/ws/**",
                                "/api/auth/first-login/change-password",
                                "/api/auth/social/sync",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
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
                                "/api/thermal-sources/**",
                                "/api/simulation-map/**",
                                "/api/planning/environment/**",
                                "/api/internal/v1/drone-telemetry/*",
                                "/api/missions/**",
                                "/api/missions/*/images",
                                "/api/missions/*/media",
                                "/api/orders/**"))

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(jwt -> jwt
                                .decoder(cognitoAccessTokenDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(activeAccountFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            log.warn(
                    "Authentication failed. method={}, path={}, reason={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage()
            );
            response.sendError(401);
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Operator-Id"));
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
