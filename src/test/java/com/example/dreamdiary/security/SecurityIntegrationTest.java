package com.example.dreamdiary.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "SQLITE_DB_PATH=./target/test-data/security-test.db",
        "DREAM_DIARY_API_KEY=test-api-key",
        "DREAM_DIARY_OAUTH_USERNAME=test-user",
        "DREAM_DIARY_OAUTH_PASSWORD=test-pass",
        "DREAM_DIARY_OAUTH_CLIENT_ID=test-claude-client",
        "DREAM_DIARY_OAUTH_CLIENT_SECRET=test-claude-secret",
        "DREAM_DIARY_OAUTH_REDIRECT_URIS=https://claude.ai/api/mcp/auth_callback",
        "DREAM_DIARY_OAUTH_ISSUER_URL=http://localhost:8080"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void oauthDiscoveryEndpointsShouldBePublic() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("http://localhost:8080/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost:8080"));

        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8080/oauth/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8080/oauth/token"));
    }

    @Test
    void mcpShouldReturnOauthChallengeWhenTokenMissing() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": "1",
                                  "method": "initialize",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("resource_metadata=\"http://localhost:8080/.well-known/oauth-protected-resource/mcp\"")));
    }

    @Test
    void apiShouldReturnUnauthorizedWithWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/dream-entries")
                        .header("Authorization", "Bearer wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiShouldAllowAccessWithValidApiKey() throws Exception {
        mockMvc.perform(get("/api/dream-entries")
                        .header("Authorization", "Bearer test-api-key"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dream-entries")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Created with api key"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void oauthAuthorizationCodeFlowShouldAllowApiAndMcpAccess() throws Exception {
        String codeVerifier = "claude-pkce-verifier-1234567890";
        String codeChallenge = s256CodeChallenge(codeVerifier);

        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "test-claude-client")
                        .param("redirect_uri", "https://claude.ai/api/mcp/auth_callback")
                        .param("scope", "dreamdiary.read dreamdiary.write")
                        .param("state", "xyz-state")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk());

        MvcResult authorizeResult = mockMvc.perform(post("/oauth/authorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("response_type=code&client_id=test-claude-client&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fmcp%2Fauth_callback"
                                + "&scope=dreamdiary.read%20dreamdiary.write&state=xyz-state&code_challenge="
                                + codeChallenge
                                + "&code_challenge_method=S256&username=test-user&password=test-pass"))
                .andExpect(status().isFound())
                .andReturn();

        String code = extractQueryParam(authorizeResult.getResponse().getHeader("Location"), "code");

        String token = exchangeAuthorizationCodeForToken(code, codeVerifier);

        mockMvc.perform(get("/api/dream-entries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": "2",
                                  "method": "tools/list",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }

    @Test
    void oauthTokenEndpointShouldRejectWrongClientSecret() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=password&username=test-user&password=test-pass&client_id=test-claude-client&client_secret=wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    private String exchangeAuthorizationCodeForToken(String code, String codeVerifier) throws Exception {
        String responseBody = mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("grant_type=authorization_code&client_id=test-claude-client&client_secret=test-claude-secret"
                                + "&code=" + code
                                + "&redirect_uri=https%3A%2F%2Fclaude.ai%2Fapi%2Fmcp%2Fauth_callback"
                                + "&code_verifier=" + codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode responseJson = objectMapper.readTree(responseBody);
        return responseJson.get("access_token").asText();
    }

    private String extractQueryParam(String locationHeader, String parameterName) {
        URI uri = URI.create(locationHeader);
        String query = uri.getRawQuery();
        if (query == null) {
            throw new IllegalStateException("Redirect URI missing query parameters.");
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(parameterName)) {
                return parts[1];
            }
        }
        throw new IllegalStateException("Parameter '" + parameterName + "' missing from redirect URI.");
    }

    private String s256CodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
