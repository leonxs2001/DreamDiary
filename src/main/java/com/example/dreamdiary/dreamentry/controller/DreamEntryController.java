package com.example.dreamdiary.dreamentry.controller;

import com.example.dreamdiary.dreamentry.dto.CreateDreamEntryRequest;
import com.example.dreamdiary.dreamentry.dto.DreamEntryResponse;
import com.example.dreamdiary.dreamentry.dto.PagedResponse;
import com.example.dreamdiary.dreamentry.dto.UpdateDreamEntryTextRequest;
import com.example.dreamdiary.dreamentry.service.DreamEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dream-entries")
@Tag(name = "Dream Entries", description = "Endpoints to create, update, search and delete dream entries")
public class DreamEntryController {

    private final DreamEntryService service;

    public DreamEntryController(DreamEntryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createDreamEntry",
            summary = "Create a dream entry",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Dream entry created"),
                    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
            }
    )
    public DreamEntryResponse create(@Valid @RequestBody CreateDreamEntryRequest request) {
        return service.create(request.text(), request.dreamDate());
    }

    @PatchMapping("/{id}/text")
    @Operation(
            operationId = "updateDreamEntryText",
            summary = "Update only the text of a dream entry",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public DreamEntryResponse updateText(@PathVariable Long id, @Valid @RequestBody UpdateDreamEntryTextRequest request) {
        return service.updateText(id, request.text());
    }

    @GetMapping
    @Operation(
            operationId = "searchDreamEntries",
            summary = "Search dream entries by day, timespan and text",
            description = "Rule: if day is provided, start and end are not allowed.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public PagedResponse<DreamEntryResponse> search(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate day,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant end,
            @RequestParam(required = false)
            String q,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<DreamEntryResponse> page = service.search(day, start, end, q, pageable);
        return PagedResponse.from(page);
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getDreamEntryById",
            summary = "Get one dream entry by id",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dream entry found"),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Dream entry not found",
                            content = @Content(
                                    schema = @Schema(implementation = com.example.dreamdiary.common.api.ApiError.class),
                                    examples = @ExampleObject(value = """
                                            {
                                              "timestamp": "2026-03-22T10:15:30Z",
                                              "status": 404,
                                              "error": "Not Found",
                                              "message": "Dream entry with id 99 was not found.",
                                              "path": "/api/dream-entries/99"
                                            }
                                            """)
                            ))
            }
    )
    public DreamEntryResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "deleteDreamEntry",
            summary = "Delete a dream entry",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
