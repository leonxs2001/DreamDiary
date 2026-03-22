package com.example.dreamdiary.dreamentry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dreamdiary.common.exception.InvalidRequestException;
import com.example.dreamdiary.dreamentry.dto.DreamEntryResponse;
import com.example.dreamdiary.dreamentry.model.DreamEntry;
import com.example.dreamdiary.dreamentry.repository.DreamEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DreamEntryServiceTest {

    @Mock
    private DreamEntryRepository repository;

    @InjectMocks
    private DreamEntryService service;

    @Test
    void createShouldPersistEntryWithCreatedAt() {
        when(repository.save(any(DreamEntry.class))).thenAnswer(invocation -> {
            DreamEntry saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        DreamEntryResponse response = service.create("I was flying over mountains.");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNull();
        assertThat(response.text()).isEqualTo("I was flying over mountains.");
        verify(repository).save(any(DreamEntry.class));
    }

    @Test
    void updateTextShouldSetUpdatedAt() {
        DreamEntry existing = new DreamEntry();
        existing.setId(42L);
        existing.setText("Old text");
        existing.setCreatedAt(Instant.parse("2026-03-20T08:00:00Z"));

        when(repository.findById(42L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        DreamEntryResponse response = service.updateText(42L, "New text");

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.text()).isEqualTo("New text");
        assertThat(response.updatedAt()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void searchShouldRejectDayTogetherWithStartOrEnd() {
        assertThatThrownBy(() -> service.search(
                LocalDate.parse("2026-03-22"),
                Instant.parse("2026-03-22T00:00:00Z"),
                null,
                null,
                Pageable.ofSize(20)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("day");
    }
}
