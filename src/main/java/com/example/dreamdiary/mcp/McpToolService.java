package com.example.dreamdiary.mcp;

import com.example.dreamdiary.common.exception.InvalidRequestException;
import com.example.dreamdiary.common.exception.ResourceNotFoundException;
import com.example.dreamdiary.dreamentry.dto.DreamEntryResponse;
import com.example.dreamdiary.dreamentry.dto.PagedResponse;
import com.example.dreamdiary.dreamentry.service.DreamEntryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class McpToolService {

    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final DreamEntryService dreamEntryService;
    private final ObjectMapper objectMapper;

    public McpToolService(DreamEntryService dreamEntryService, ObjectMapper objectMapper) {
        this.dreamEntryService = dreamEntryService;
        this.objectMapper = objectMapper;
    }

    public ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        tools.add(toolDefinition(
                "createDreamEntry",
                "Create a dream entry.",
                """
                        {
                          "type": "object",
                          "required": ["text"],
                          "properties": {
                            "text": { "type": "string", "minLength": 1 },
                            "dreamDate": { "type": "string", "format": "date" }
                          }
                        }
                        """
        ));

        tools.add(toolDefinition(
                "searchDreamEntries",
                "Search dream entries by day, time range and text query.",
                """
                        {
                          "type": "object",
                          "properties": {
                            "day": { "type": "string", "format": "date" },
                            "start": { "type": "string", "format": "date-time" },
                            "end": { "type": "string", "format": "date-time" },
                            "q": { "type": "string" },
                            "page": { "type": "integer", "minimum": 0, "default": 0 },
                            "size": { "type": "integer", "minimum": 1, "default": 20 },
                            "sort": { "type": "string", "default": "createdAt,desc" }
                          }
                        }
                        """
        ));

        tools.add(toolDefinition(
                "getDreamEntryById",
                "Get one dream entry by id.",
                """
                        {
                          "type": "object",
                          "required": ["id"],
                          "properties": {
                            "id": { "type": "integer", "minimum": 1 }
                          }
                        }
                        """
        ));

        tools.add(toolDefinition(
                "updateDreamEntryText",
                "Update only the text field of a dream entry.",
                """
                        {
                          "type": "object",
                          "required": ["id", "text"],
                          "properties": {
                            "id": { "type": "integer", "minimum": 1 },
                            "text": { "type": "string", "minLength": 1 }
                          }
                        }
                        """
        ));

        tools.add(toolDefinition(
                "deleteDreamEntry",
                "Delete a dream entry.",
                """
                        {
                          "type": "object",
                          "required": ["id"],
                          "properties": {
                            "id": { "type": "integer", "minimum": 1 }
                          }
                        }
                        """
        ));

        return result;
    }

    public ObjectNode callTool(String toolName, JsonNode arguments) {
        try {
            JsonNode resultData = switch (toolName) {
                case "createDreamEntry" -> createDreamEntry(arguments);
                case "searchDreamEntries" -> searchDreamEntries(arguments);
                case "getDreamEntryById" -> getDreamEntryById(arguments);
                case "updateDreamEntryText" -> updateDreamEntryText(arguments);
                case "deleteDreamEntry" -> deleteDreamEntry(arguments);
                default -> throw new InvalidRequestException("Unknown tool '" + toolName + "'.");
            };
            return successfulToolResponse(resultData);
        } catch (InvalidRequestException | ResourceNotFoundException | DateTimeParseException exception) {
            return failedToolResponse(exception.getMessage());
        }
    }

    private ObjectNode createDreamEntry(JsonNode arguments) {
        String text = requireText(arguments, "text");
        LocalDate dreamDate = optionalLocalDate(arguments, "dreamDate");
        DreamEntryResponse response = dreamEntryService.create(text, dreamDate);
        return objectMapper.valueToTree(response);
    }

    private ObjectNode searchDreamEntries(JsonNode arguments) {
        LocalDate day = optionalLocalDate(arguments, "day");
        Instant start = optionalInstant(arguments, "start");
        Instant end = optionalInstant(arguments, "end");
        String query = optionalString(arguments, "q");
        int page = optionalInt(arguments, "page", DEFAULT_PAGE);
        int size = optionalInt(arguments, "size", DEFAULT_SIZE);
        String sortExpression = optionalString(arguments, "sort");

        if (page < 0) {
            throw new InvalidRequestException("Parameter 'page' must be >= 0.");
        }
        if (size < 1) {
            throw new InvalidRequestException("Parameter 'size' must be >= 1.");
        }

        Pageable pageable = PageRequest.of(page, size, parseSort(sortExpression));
        Page<DreamEntryResponse> found = dreamEntryService.search(day, start, end, query, pageable);
        return objectMapper.valueToTree(PagedResponse.from(found));
    }

    private ObjectNode getDreamEntryById(JsonNode arguments) {
        Long id = requireLong(arguments, "id");
        DreamEntryResponse response = dreamEntryService.getById(id);
        return objectMapper.valueToTree(response);
    }

    private ObjectNode updateDreamEntryText(JsonNode arguments) {
        Long id = requireLong(arguments, "id");
        String text = requireText(arguments, "text");
        DreamEntryResponse response = dreamEntryService.updateText(id, text);
        return objectMapper.valueToTree(response);
    }

    private ObjectNode deleteDreamEntry(JsonNode arguments) {
        Long id = requireLong(arguments, "id");
        dreamEntryService.delete(id);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("deleted", true);
        response.put("id", id);
        return response;
    }

    private ObjectNode toolDefinition(String name, String description, String schemaJson) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        try {
            tool.set("inputSchema", objectMapper.readTree(schemaJson));
            return tool;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid MCP tool schema for " + name, exception);
        }
    }

    private ObjectNode successfulToolResponse(JsonNode resultData) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", stringify(resultData));
        result.set("structuredContent", resultData);
        result.put("isError", false);
        return result;
    }

    private ObjectNode failedToolResponse(String message) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", message);
        result.put("isError", true);
        return result;
    }

    private String stringify(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"message\":\"Unable to serialize tool output.\"}";
        }
    }

    private String requireText(JsonNode arguments, String field) {
        String value = optionalString(arguments, field);
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException("Field '" + field + "' must not be blank.");
        }
        return value;
    }

    private Long requireLong(JsonNode arguments, String field) {
        JsonNode node = field(arguments, field);
        if (!node.isIntegralNumber() || node.longValue() < 1) {
            throw new InvalidRequestException("Field '" + field + "' must be a positive integer.");
        }
        return node.longValue();
    }

    private LocalDate optionalLocalDate(JsonNode arguments, String field) {
        String value = optionalString(arguments, field);
        return StringUtils.hasText(value) ? LocalDate.parse(value) : null;
    }

    private Instant optionalInstant(JsonNode arguments, String field) {
        String value = optionalString(arguments, field);
        return StringUtils.hasText(value) ? Instant.parse(value) : null;
    }

    private int optionalInt(JsonNode arguments, String field, int defaultValue) {
        JsonNode node = arguments == null ? null : arguments.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isIntegralNumber()) {
            throw new InvalidRequestException("Field '" + field + "' must be an integer.");
        }
        return node.intValue();
    }

    private String optionalString(JsonNode arguments, String field) {
        JsonNode node = arguments == null ? null : arguments.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new InvalidRequestException("Field '" + field + "' must be a string.");
        }
        return node.textValue();
    }

    private JsonNode field(JsonNode arguments, String field) {
        JsonNode node = arguments == null ? null : arguments.get(field);
        if (node == null || node.isNull()) {
            throw new InvalidRequestException("Missing required field '" + field + "'.");
        }
        return node;
    }

    private Sort parseSort(String sortExpression) {
        String effectiveSort = StringUtils.hasText(sortExpression) ? sortExpression : DEFAULT_SORT;
        String[] parts = effectiveSort.split(",", 2);
        String property = parts[0].trim();
        if (!StringUtils.hasText(property)) {
            throw new InvalidRequestException("Sort property must not be blank.");
        }

        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2 && StringUtils.hasText(parts[1])) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("Unsupported sort direction '" + parts[1].trim() + "'.");
            }
        }

        return Sort.by(direction, property);
    }
}
