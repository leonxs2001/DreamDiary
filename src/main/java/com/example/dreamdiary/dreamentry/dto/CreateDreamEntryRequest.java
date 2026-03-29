package com.example.dreamdiary.dreamentry.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateDreamEntryRequest(
        @NotBlank(message = "text must not be blank")
        String text,
        LocalDate dreamDate
) {
}
