package com.example.dreamdiary.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthTokenService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String CODE_CHALLENGE_METHOD_S256 = "S256";

    private final AppSecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AccessTokenRecord> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, RefreshTokenRecord> refreshTokens = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCodeRecord> authorizationCodes = new ConcurrentHashMap<>();

    public OAuthTokenService(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public boolean isEnabled() {
        AppSecurityProperties.Oauth oauth = securityProperties.getOauth();
        return StringUtils.hasText(oauth.getUsername())
                && StringUtils.hasText(oauth.getPassword())
                && StringUtils.hasText(oauth.getClientId())
                && !oauth.redirectUriList().isEmpty()
                && StringUtils.hasText(oauth.getIssuerUrl());
    }

    public Optional<AuthenticatedOAuthUser> authenticate(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return Optional.empty();
        }

        AccessTokenRecord record = activeTokens.get(bearerToken);
        if (record == null) {
            return Optional.empty();
        }
        if (!record.expiresAt().isAfter(Instant.now())) {
            activeTokens.remove(bearerToken);
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedOAuthUser(record.username(), record.scope()));
    }

    public AuthorizationRequest validateAuthorizationRequest(
            String responseType,
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod,
            String resource) {
        ensureEnabled();

        if (!"code".equals(responseType)) {
            throw new OAuthException("unsupported_response_type", "Only response_type=code is supported.", HttpStatus.BAD_REQUEST);
        }
        if (!constantTimeEquals(configuredClientId(), clientId)) {
            throw new OAuthException("invalid_client", "Unknown client_id.", HttpStatus.UNAUTHORIZED);
        }
        if (!isAllowedRedirectUri(redirectUri)) {
            throw new OAuthException("invalid_request", "Invalid redirect_uri.", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(codeChallenge)) {
            throw new OAuthException("invalid_request", "code_challenge is required.", HttpStatus.BAD_REQUEST);
        }
        if (!CODE_CHALLENGE_METHOD_S256.equals(codeChallengeMethod)) {
            throw new OAuthException("invalid_request", "Only code_challenge_method=S256 is supported.", HttpStatus.BAD_REQUEST);
        }

        String effectiveScope = StringUtils.hasText(scope) ? scope.trim() : configuredScopes();
        validateScopeSubset(effectiveScope);
        return new AuthorizationRequest(responseType, clientId, redirectUri, effectiveScope, state, codeChallenge, codeChallengeMethod, resource);
    }

    public String authorizeAndIssueCode(AuthorizationRequest request, String username, String password) {
        ensureEnabled();
        if (!constantTimeEquals(configuredUsername(), username) || !constantTimeEquals(configuredPassword(), password)) {
            throw new OAuthException("access_denied", "Invalid username or password.", HttpStatus.UNAUTHORIZED);
        }

        String code = randomToken(32);
        Instant expiresAt = Instant.now().plus(securityProperties.getOauth().getAuthorizationCodeTtl());
        authorizationCodes.put(code, new AuthorizationCodeRecord(
                request.clientId(),
                request.redirectUri(),
                request.codeChallenge(),
                request.scope(),
                request.resource(),
                configuredUsername(),
                expiresAt
        ));
        return code;
    }

    public TokenResponse exchangeAuthorizationCode(
            String code,
            String redirectUri,
            String codeVerifier,
            String clientId,
            String clientSecret) {
        ensureEnabled();
        ensureClientAuthenticated(clientId, clientSecret);

        if (!StringUtils.hasText(code)) {
            throw new OAuthException("invalid_request", "code is required.", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(codeVerifier)) {
            throw new OAuthException("invalid_request", "code_verifier is required.", HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(redirectUri)) {
            throw new OAuthException("invalid_request", "redirect_uri is required.", HttpStatus.BAD_REQUEST);
        }

        AuthorizationCodeRecord record = authorizationCodes.remove(code);
        if (record == null) {
            throw new OAuthException("invalid_grant", "Invalid or already used authorization code.", HttpStatus.BAD_REQUEST);
        }
        if (!record.expiresAt().isAfter(Instant.now())) {
            throw new OAuthException("invalid_grant", "Authorization code is expired.", HttpStatus.BAD_REQUEST);
        }
        if (!constantTimeEquals(record.clientId(), clientId)) {
            throw new OAuthException("invalid_grant", "Authorization code was not issued for this client.", HttpStatus.BAD_REQUEST);
        }
        if (!constantTimeEquals(record.redirectUri(), redirectUri)) {
            throw new OAuthException("invalid_grant", "redirect_uri does not match.", HttpStatus.BAD_REQUEST);
        }
        if (!verifyPkce(record.codeChallenge(), codeVerifier)) {
            throw new OAuthException("invalid_grant", "Invalid PKCE code_verifier.", HttpStatus.BAD_REQUEST);
        }

        return issueTokenPair(record.username(), record.scope(), record.clientId(), record.resource());
    }

    public TokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret) {
        ensureEnabled();
        ensureClientAuthenticated(clientId, clientSecret);

        if (!StringUtils.hasText(refreshToken)) {
            throw new OAuthException("invalid_request", "refresh_token is required.", HttpStatus.BAD_REQUEST);
        }

        RefreshTokenRecord record = refreshTokens.get(refreshToken);
        if (record == null) {
            throw new OAuthException("invalid_grant", "Invalid refresh token.", HttpStatus.BAD_REQUEST);
        }
        if (!record.expiresAt().isAfter(Instant.now())) {
            refreshTokens.remove(refreshToken);
            throw new OAuthException("invalid_grant", "Refresh token is expired.", HttpStatus.BAD_REQUEST);
        }
        if (!constantTimeEquals(record.clientId(), clientId)) {
            throw new OAuthException("invalid_grant", "Refresh token was not issued for this client.", HttpStatus.BAD_REQUEST);
        }

        return issueTokenPair(record.username(), record.scope(), record.clientId(), record.resource());
    }

    public TokenResponse issuePasswordToken(String username, String password, String clientId, String clientSecret) {
        ensureEnabled();
        ensureClientAuthenticated(clientId, clientSecret);

        if (!constantTimeEquals(configuredUsername(), username) || !constantTimeEquals(configuredPassword(), password)) {
            throw new OAuthException("invalid_grant", "Invalid username or password.", HttpStatus.BAD_REQUEST);
        }

        return issueTokenPair(configuredUsername(), configuredScopes(), configuredClientId(), mcpResourceUrl());
    }

    public String mcpChallengeHeaderValue(String errorDescription) {
        String safeDescription = StringUtils.hasText(errorDescription) ? errorDescription : "Authentication required.";
        return "Bearer error=\"invalid_token\", "
                + "error_description=\"" + escapeHeaderValue(safeDescription) + "\", "
                + "resource_metadata=\"" + oauthProtectedResourceMetadataUrlForMcp() + "\"";
    }

    public Map<String, Object> protectedResourceMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", mcpResourceUrl());
        metadata.put("authorization_servers", List.of(normalizeUrl(configuredIssuerUrl())));
        metadata.put("bearer_methods_supported", List.of("header"));
        metadata.put("scopes_supported", configuredScopeList());
        return metadata;
    }

    public Map<String, Object> authorizationServerMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", normalizeUrl(configuredIssuerUrl()));
        metadata.put("authorization_endpoint", normalizeUrl(configuredIssuerUrl()) + "/oauth/authorize");
        metadata.put("token_endpoint", normalizeUrl(configuredIssuerUrl()) + "/oauth/token");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token", "password"));
        metadata.put("code_challenge_methods_supported", List.of(CODE_CHALLENGE_METHOD_S256));
        metadata.put("scopes_supported", configuredScopeList());
        metadata.put("token_endpoint_auth_methods_supported", tokenEndpointAuthMethodsSupported());
        return metadata;
    }

    public String configuredUsername() {
        return securityProperties.getOauth().getUsername();
    }

    public String configuredClientId() {
        return securityProperties.getOauth().getClientId();
    }

    public String configuredRedirectUrisAsCsv() {
        return String.join(", ", securityProperties.getOauth().redirectUriList());
    }

    public List<String> tokenEndpointAuthMethodsSupported() {
        if (StringUtils.hasText(configuredClientSecret())) {
            return List.of("client_secret_post", "client_secret_basic");
        }
        return List.of("none");
    }

    public String configuredScopes() {
        String configured = securityProperties.getOauth().getScopes();
        return StringUtils.hasText(configured) ? configured.trim() : "dreamdiary.read dreamdiary.write";
    }

    public String oauthProtectedResourceMetadataUrlForMcp() {
        return normalizeUrl(configuredIssuerUrl()) + "/.well-known/oauth-protected-resource/mcp";
    }

    public String configuredIssuerUrl() {
        return securityProperties.getOauth().getIssuerUrl();
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            return false;
        }
        for (String allowed : securityProperties.getOauth().redirectUriList()) {
            if (constantTimeEquals(allowed, redirectUri)) {
                return true;
            }
        }
        return false;
    }

    public void ensureClientAuthenticated(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientId)) {
            throw new OAuthException("invalid_client", "client_id is required.", HttpStatus.UNAUTHORIZED);
        }
        if (!constantTimeEquals(configuredClientId(), clientId)) {
            throw new OAuthException("invalid_client", "Unknown client_id.", HttpStatus.UNAUTHORIZED);
        }

        String configuredSecret = configuredClientSecret();
        if (!StringUtils.hasText(configuredSecret)) {
            return;
        }
        if (!StringUtils.hasText(clientSecret) || !constantTimeEquals(configuredSecret, clientSecret)) {
            throw new OAuthException("invalid_client", "Invalid client credentials.", HttpStatus.UNAUTHORIZED);
        }
    }

    private TokenResponse issueTokenPair(String username, String scope, String clientId, String resource) {
        Instant accessTokenExpiresAt = Instant.now().plus(securityProperties.getOauth().getTokenTtl());
        String accessToken = randomToken(48);
        activeTokens.put(accessToken, new AccessTokenRecord(username, scope, clientId, resource, accessTokenExpiresAt));

        Instant refreshTokenExpiresAt = Instant.now().plus(securityProperties.getOauth().getRefreshTokenTtl());
        String refreshToken = randomToken(48);
        refreshTokens.put(refreshToken, new RefreshTokenRecord(username, scope, clientId, resource, refreshTokenExpiresAt));

        return new TokenResponse(
                accessToken,
                TOKEN_TYPE,
                Math.max(0, Duration.between(Instant.now(), accessTokenExpiresAt).getSeconds()),
                scope,
                refreshToken
        );
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new OAuthException(
                    "invalid_server_configuration",
                    "OAuth is not fully configured. Configure username, password, client_id, redirect_uris and issuer_url in .env.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateScopeSubset(String requestedScope) {
        List<String> allowed = configuredScopeList();
        if (!StringUtils.hasText(requestedScope)) {
            return;
        }
        for (String scope : requestedScope.trim().split("\\s+")) {
            if (!allowed.contains(scope)) {
                throw new OAuthException("invalid_scope", "Unsupported scope '" + scope + "'.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private List<String> configuredScopeList() {
        String scopeText = configuredScopes();
        List<String> scopes = new ArrayList<>();
        for (String scope : scopeText.split("\\s+")) {
            String normalized = scope.trim();
            if (!normalized.isEmpty()) {
                scopes.add(normalized);
            }
        }
        return scopes;
    }

    private String configuredPassword() {
        return securityProperties.getOauth().getPassword();
    }

    private String configuredClientSecret() {
        return securityProperties.getOauth().getClientSecret();
    }

    private String mcpResourceUrl() {
        return normalizeUrl(configuredIssuerUrl()) + "/mcp";
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean verifyPkce(String codeChallenge, String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
            return constantTimeEquals(codeChallenge, encoded);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String randomToken(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String escapeHeaderValue(String value) {
        return value.replace("\"", "'");
    }

    private boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }

    public record AuthorizationRequest(
            String responseType,
            String clientId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod,
            String resource) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String scope,
            String refreshToken) {
    }

    public record AuthenticatedOAuthUser(String username, String scope) {
    }

    private record AccessTokenRecord(
            String username,
            String scope,
            String clientId,
            String resource,
            Instant expiresAt) {
    }

    private record RefreshTokenRecord(
            String username,
            String scope,
            String clientId,
            String resource,
            Instant expiresAt) {
    }

    private record AuthorizationCodeRecord(
            String clientId,
            String redirectUri,
            String codeChallenge,
            String scope,
            String resource,
            String username,
            Instant expiresAt) {
    }

    public static class OAuthException extends RuntimeException {

        private final String error;
        private final HttpStatus status;

        public OAuthException(String error, String description, HttpStatus status) {
            super(description);
            this.error = error;
            this.status = status;
        }

        public String error() {
            return error;
        }

        public HttpStatus status() {
            return status;
        }
    }
}
