package com.example.dreamdiary.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "SQLITE_DB_PATH=./target/test-data/security-test.db",
        "DREAM_DIARY_USERS=tester:secret123",
        "OAUTH_CLIENT_ID=test-client",
        "OAUTH_CLIENT_SECRET=test-secret",
        "OAUTH_REDIRECT_URI=https://example.com/callback",
        "OAUTH_SCOPES=openid,profile,dream.read,dream.write",
        "OAUTH_ISSUER=http://localhost:8080"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void apiShouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/dream-entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readScopeShouldAllowGetAndBlockWrite() throws Exception {
        mockMvc.perform(get("/api/dream-entries")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_dream.read"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dream-entries")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_dream.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Not allowed with read scope only"
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
