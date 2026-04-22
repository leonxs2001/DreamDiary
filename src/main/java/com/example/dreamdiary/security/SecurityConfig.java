package com.example.dreamdiary.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            AppSecurityProperties appSecurityProperties,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        assertApiKeyConfigured(appSecurityProperties.getApiKey());

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/",
                        "/error",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/openapi.yaml",
                        "/oauth/token",
                        "/oauth/authorize",
                        "/.well-known/**"
                ).permitAll()
                .requestMatchers("/api/**", "/mcp", "/mcp/**").authenticated()
                .anyRequest().permitAll());

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void assertApiKeyConfigured(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DREAM_DIARY_API_KEY must not be empty.");
        }
    }
}
