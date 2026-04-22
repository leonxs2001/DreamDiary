package com.example.dreamdiary.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String apiKey;
    private final Oauth oauth = new Oauth();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Oauth getOauth() {
        return oauth;
    }

    public static class Oauth {

        private String username;
        private String password;
        private String clientId;
        private String clientSecret;
        private String redirectUris = "https://claude.ai/api/mcp/auth_callback";
        private String issuerUrl = "http://localhost:8080";
        private String scopes = "dreamdiary.read dreamdiary.write";
        private Duration tokenTtl = Duration.ofHours(8);
        private Duration refreshTokenTtl = Duration.ofDays(30);
        private Duration authorizationCodeTtl = Duration.ofMinutes(5);

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUris() {
            return redirectUris;
        }

        public void setRedirectUris(String redirectUris) {
            this.redirectUris = redirectUris;
        }

        public List<String> redirectUriList() {
            return Arrays.stream((redirectUris == null ? "" : redirectUris).split(","))
                    .map(String::trim)
                    .filter(uri -> !uri.isEmpty())
                    .collect(Collectors.toList());
        }

        public String getIssuerUrl() {
            return issuerUrl;
        }

        public void setIssuerUrl(String issuerUrl) {
            this.issuerUrl = issuerUrl;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public Duration getAuthorizationCodeTtl() {
            return authorizationCodeTtl;
        }

        public void setAuthorizationCodeTtl(Duration authorizationCodeTtl) {
            this.authorizationCodeTtl = authorizationCodeTtl;
        }
    }
}
