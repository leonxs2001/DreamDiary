package com.example.dreamdiary.security;

import com.example.dreamdiary.common.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PROTECTED_PATH_PREFIXES = List.of("/api/", "/mcp");
    private static final String MCP_PATH_PREFIX = "/mcp";

    private final AppSecurityProperties securityProperties;
    private final OAuthTokenService oauthTokenService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            AppSecurityProperties securityProperties,
            OAuthTokenService oauthTokenService,
            ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.oauthTokenService = oauthTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String prefix : PROTECTED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String configuredApiKey = securityProperties.getApiKey();
        if (!StringUtils.hasText(configuredApiKey)) {
            throw new IllegalStateException("DREAM_DIARY_API_KEY must not be empty.");
        }

        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, request.getRequestURI(), "Missing bearer token.");
            return;
        }

        String providedBearer = authorization.substring(BEARER_PREFIX.length());
        if (!StringUtils.hasText(providedBearer)) {
            writeUnauthorized(response, request.getRequestURI(), "Missing bearer token.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = resolveAuthentication(configuredApiKey, providedBearer);
        if (authentication == null) {
            writeUnauthorized(response, request.getRequestURI(), "Invalid bearer token.");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken resolveAuthentication(String configuredApiKey, String providedBearer) {
        if (constantTimeEquals(configuredApiKey, providedBearer)) {
            return new UsernamePasswordAuthenticationToken(
                    "api-key-client",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
            );
        }

        return oauthTokenService.authenticate(providedBearer)
                .map(user -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
                    authorities.add(new SimpleGrantedAuthority("ROLE_OAUTH_CLIENT"));
                    return new UsernamePasswordAuthenticationToken(user.username(), null, authorities);
                })
                .orElse(null);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        ApiError apiError = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                path,
                null
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        if (path.startsWith(MCP_PATH_PREFIX)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, oauthTokenService.mcpChallengeHeaderValue(message));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiError);
    }
}
