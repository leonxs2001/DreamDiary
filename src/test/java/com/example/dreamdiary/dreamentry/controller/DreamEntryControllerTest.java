package com.example.dreamdiary.dreamentry.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dreamdiary.common.exception.GlobalExceptionHandler;
import com.example.dreamdiary.dreamentry.dto.DreamEntryResponse;
import com.example.dreamdiary.dreamentry.service.DreamEntryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DreamEntryController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class DreamEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DreamEntryService service;

    @Test
    void createShouldReturnCreatedEntry() throws Exception {
        Instant createdAt = Instant.parse("2026-03-22T09:00:00Z");
        DreamEntryResponse response = new DreamEntryResponse(1L, "Dream text", createdAt, null);
        when(service.create(eq("Dream text"))).thenReturn(response);

        mockMvc.perform(post("/api/dream-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Dream text"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.text").value("Dream text"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-22T09:00:00Z"));
    }

    @Test
    void createShouldReturnBadRequestForBlankText() throws Exception {
        mockMvc.perform(post("/api/dream-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.text").exists());
    }

    @Test
    void patchShouldUpdateText() throws Exception {
        Instant createdAt = Instant.parse("2026-03-21T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-03-22T10:30:00Z");
        DreamEntryResponse response = new DreamEntryResponse(7L, "Updated text", createdAt, updatedAt);
        when(service.updateText(eq(7L), eq("Updated text"))).thenReturn(response);

        mockMvc.perform(patch("/api/dream-entries/7/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text": "Updated text"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.text").value("Updated text"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-22T10:30:00Z"));
    }
}
