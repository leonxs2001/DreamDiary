package com.example.dreamdiary.dreamentry.service;

import com.example.dreamdiary.common.exception.InvalidRequestException;
import com.example.dreamdiary.common.exception.ResourceNotFoundException;
import com.example.dreamdiary.dreamentry.dto.DreamEntryResponse;
import com.example.dreamdiary.dreamentry.model.DreamEntry;
import com.example.dreamdiary.dreamentry.repository.DreamEntryRepository;
import jakarta.persistence.criteria.Expression;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DreamEntryService {

    private static final Logger log = LoggerFactory.getLogger(DreamEntryService.class);

    private final DreamEntryRepository repository;

    public DreamEntryService(DreamEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DreamEntryResponse create(String text) {
        ensureTextIsValid(text);
        DreamEntry entry = new DreamEntry();
        entry.setText(text);
        entry.setCreatedAt(Instant.now());
        DreamEntry savedEntry = repository.save(entry);
        log.debug("Created dream entry with id={}", savedEntry.getId());
        return toResponse(savedEntry);
    }

    @Transactional
    public DreamEntryResponse updateText(Long id, String text) {
        ensureTextIsValid(text);
        DreamEntry entry = findEntityById(id);
        entry.setText(text);
        entry.setUpdatedAt(Instant.now());
        DreamEntry savedEntry = repository.save(entry);
        log.debug("Updated text for dream entry id={}", id);
        return toResponse(savedEntry);
    }

    public DreamEntryResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    public Page<DreamEntryResponse> search(
            LocalDate day,
            Instant start,
            Instant end,
            String queryText,
            Pageable pageable) {
        TimeRange range = resolveTimeRange(day, start, end);

        Specification<DreamEntry> specification = Specification.where(null);
        if (range.start() != null) {
            specification = specification.and(createdAtAtLeast(range.start()));
        }
        if (range.end() != null) {
            specification = specification.and(createdAtBefore(range.end()));
        }
        if (StringUtils.hasText(queryText)) {
            specification = specification.and(textContainsIgnoreCase(queryText));
        }

        return repository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        DreamEntry entry = findEntityById(id);
        repository.delete(entry);
        log.debug("Deleted dream entry id={}", id);
    }

    private TimeRange resolveTimeRange(LocalDate day, Instant start, Instant end) {
        if (day != null && (start != null || end != null)) {
            throw new InvalidRequestException("Query parameter 'day' cannot be combined with 'start' or 'end'.");
        }

        if (day != null) {
            Instant dayStart = day.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant dayEnd = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return new TimeRange(dayStart, dayEnd);
        }

        if (start != null && end != null && !start.isBefore(end)) {
            throw new InvalidRequestException("Parameter 'start' must be before parameter 'end'.");
        }

        return new TimeRange(start, end);
    }

    private Specification<DreamEntry> createdAtAtLeast(Instant start) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    private Specification<DreamEntry> createdAtBefore(Instant end) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("createdAt"), end);
    }

    private Specification<DreamEntry> textContainsIgnoreCase(String queryText) {
        String normalizedQuery = queryText.trim().toLowerCase(Locale.ROOT);
        String escapedQuery = "%" + escapeLikePattern(normalizedQuery) + "%";
        return (root, query, criteriaBuilder) -> {
            Expression<String> textLower = criteriaBuilder.lower(root.get("text"));
            return criteriaBuilder.like(textLower, escapedQuery, '\\');
        };
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private DreamEntry findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dream entry with id " + id + " was not found."));
    }

    private void ensureTextIsValid(String text) {
        if (!StringUtils.hasText(text)) {
            throw new InvalidRequestException("text must not be blank.");
        }
    }

    private DreamEntryResponse toResponse(DreamEntry entity) {
        return new DreamEntryResponse(
                entity.getId(),
                entity.getText(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
