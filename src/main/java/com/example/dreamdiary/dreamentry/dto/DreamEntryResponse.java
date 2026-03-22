package com.example.dreamdiary.dreamentry.dto;

import java.time.Instant;

public record DreamEntryResponse(
        Long id,
        String text,
        Instant createdAt,
        Instant updatedAt
) {
}
