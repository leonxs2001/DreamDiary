package com.example.dreamdiary.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "SQLITE_DB_PATH=./target/test-data/security-test.db",
        "DREAM_DIARY_API_KEY=test-api-key"
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
    void apiShouldReturnUnauthorizedWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/dream-entries"))
                .andExpect(status().isUnauthorized());
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
}
