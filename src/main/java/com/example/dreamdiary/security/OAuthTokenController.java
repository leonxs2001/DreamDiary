package com.example.dreamdiary.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthTokenController {

    private final OAuthTokenService oauthTokenService;

    public OAuthTokenController(OAuthTokenService oauthTokenService) {
        this.oauthTokenService = oauthTokenService;
    }

    @PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> issueToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "client_id", required = false) String clientIdFromBody,
            @RequestParam(value = "client_secret", required = false) String clientSecretFromBody,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password) {
        try {
            OAuthClientCredentials clientCredentials = resolveClientCredentials(
                    authorizationHeader, clientIdFromBody, clientSecretFromBody);
            OAuthTokenService.TokenResponse tokenResponse = switch (grantType) {
                case "authorization_code" -> oauthTokenService.exchangeAuthorizationCode(
                        code,
                        redirectUri,
                        codeVerifier,
                        clientCredentials.clientId(),
                        clientCredentials.clientSecret()
                );
                case "refresh_token" -> oauthTokenService.refreshAccessToken(
                        refreshToken,
                        clientCredentials.clientId(),
                        clientCredentials.clientSecret()
                );
                case "password" -> oauthTokenService.issuePasswordToken(
                        username,
                        password,
                        clientCredentials.clientId(),
                        clientCredentials.clientSecret()
                );
                default -> throw new OAuthTokenService.OAuthException(
                        "unsupported_grant_type",
                        "Unsupported grant_type '" + grantType + "'.",
                        org.springframework.http.HttpStatus.BAD_REQUEST
                );
            };
            return ResponseEntity.ok(tokenSuccess(tokenResponse));
        } catch (OAuthTokenService.OAuthException exception) {
            return ResponseEntity.status(exception.status()).body(tokenError(exception.error(), exception.getMessage()));
        }
    }

    private OAuthClientCredentials resolveClientCredentials(
            String authorizationHeader,
            String clientIdFromBody,
            String clientSecretFromBody) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Basic ")) {
            String encodedPart = authorizationHeader.substring("Basic ".length()).trim();
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(encodedPart);
                String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
                int separator = decoded.indexOf(':');
                if (separator < 0) {
                    throw new OAuthTokenService.OAuthException(
                            "invalid_client",
                            "Malformed client credentials.",
                            org.springframework.http.HttpStatus.UNAUTHORIZED
                    );
                }
                return new OAuthClientCredentials(
                        decoded.substring(0, separator),
                        decoded.substring(separator + 1)
                );
            } catch (IllegalArgumentException exception) {
                throw new OAuthTokenService.OAuthException(
                        "invalid_client",
                        "Malformed Basic authorization header.",
                        org.springframework.http.HttpStatus.UNAUTHORIZED
                );
            }
        }
        return new OAuthClientCredentials(clientIdFromBody, clientSecretFromBody);
    }

    private Map<String, Object> tokenSuccess(OAuthTokenService.TokenResponse tokenResponse) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("access_token", tokenResponse.accessToken());
        payload.put("token_type", tokenResponse.tokenType());
        payload.put("expires_in", tokenResponse.expiresIn());
        payload.put("scope", tokenResponse.scope());
        payload.put("refresh_token", tokenResponse.refreshToken());
        return payload;
    }

    private Map<String, Object> tokenError(String error, String description) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("error_description", description);
        return payload;
    }

    private record OAuthClientCredentials(String clientId, String clientSecret) {
    }
}
