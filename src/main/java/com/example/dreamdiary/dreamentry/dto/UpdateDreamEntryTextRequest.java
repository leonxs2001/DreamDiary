package com.example.dreamdiary.dreamentry.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDreamEntryTextRequest(
        @NotBlank(message = "text must not be blank")
        String text
) {
}
