package com.ondemandmonitoring.controlgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> CONTROL_ROLES = Set.of(
            "DRONE_OPERATOR", "SYSTEM_OPERATOR", "ADMIN");

    @Bean
    JwtDecoder accessTokenDecoder(ControlSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuerUri()).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> tokenUse = claimValidator(
                "token_use", "access", "The token is not a Cognito access token");
        OAuth2TokenValidator<Jwt> clientId = claimValidator(
                "client_id", properties.clientId(), "The access token client_id is invalid");
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, tokenUse, clientId));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder accessTokenDecoder,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/control/**")
                        .hasAnyRole("DRONE_OPERATOR", "SYSTEM_OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(accessTokenDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ControlSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(properties.allowedOrigins().split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Range"));
        configuration.setExposedHeaders(List.of("Content-Range", "Accept-Ranges", "Content-Length"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups == null) {
                return List.of();
            }
            return groups.stream()
                    .filter(CONTROL_ROLES::contains)
                    .map(group -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + group))
                    .toList();
        });
        return converter;
    }

    private OAuth2TokenValidator<Jwt> claimValidator(
            String claim, String expected, String description) {
        return jwt -> expected.equals(jwt.getClaimAsString(claim))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", description, null));
    }
}
