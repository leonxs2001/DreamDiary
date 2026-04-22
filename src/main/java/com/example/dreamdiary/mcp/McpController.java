package com.example.dreamdiary.mcp;

import com.example.dreamdiary.common.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class McpController {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;
    private final McpToolService mcpToolService;

    public McpController(ObjectMapper objectMapper, McpToolService mcpToolService) {
        this.objectMapper = objectMapper;
        this.mcpToolService = mcpToolService;
    }

    @PostMapping(path = "/mcp")
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return ResponseEntity.badRequest().body(jsonRpcError(NullNode.instance, -32600, "Invalid Request"));
        }

        if (payload.isArray()) {
            return handleBatchRequest((ArrayNode) payload);
        }

        if (!payload.isObject()) {
            return ResponseEntity.badRequest().body(jsonRpcError(NullNode.instance, -32600, "Invalid Request"));
        }

        JsonNode response = handleSingleRequest((ObjectNode) payload);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<JsonNode> handleBatchRequest(ArrayNode requests) {
        ArrayNode responses = objectMapper.createArrayNode();
        Iterator<JsonNode> iterator = requests.iterator();
        while (iterator.hasNext()) {
            JsonNode request = iterator.next();
            if (!request.isObject()) {
                responses.add(jsonRpcError(NullNode.instance, -32600, "Invalid Request"));
                continue;
            }

            JsonNode response = handleSingleRequest((ObjectNode) request);
            if (response != null) {
                responses.add(response);
            }
        }

        if (responses.isEmpty()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        return ResponseEntity.ok(responses);
    }

    private JsonNode handleSingleRequest(ObjectNode request) {
        JsonNode id = request.get("id");
        String method = asText(request.get("method"));
        JsonNode params = request.get("params");
        boolean isNotification = id == null || id.isNull();

        if (!JSON_RPC_VERSION.equals(asText(request.get("jsonrpc"))) || !hasText(method)) {
            return isNotification ? null : jsonRpcError(normalizeId(id), -32600, "Invalid Request");
        }

        try {
            ObjectNode result = switch (method) {
                case "initialize" -> initializeResult();
                case "ping" -> objectMapper.createObjectNode();
                case "tools/list" -> mcpToolService.toolsListResult();
                case "tools/call" -> handleToolCall(params);
                case "notifications/initialized" -> null;
                default -> throw new InvalidRequestException("Method not found: " + method);
            };

            if (isNotification || result == null) {
                return null;
            }
            return jsonRpcSuccess(normalizeId(id), result);
        } catch (InvalidRequestException exception) {
            return isNotification ? null : jsonRpcError(normalizeId(id), -32601, exception.getMessage());
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "dream-diary-mcp");
        serverInfo.put("version", "1.2.0");

        result.put("instructions", "Use the available tools to create, search, update and delete dream entries.");
        return result;
    }

    private ObjectNode handleToolCall(JsonNode params) {
        if (params == null || !params.isObject()) {
            throw new InvalidRequestException("tools/call requires an object 'params'.");
        }

        String toolName = asText(params.get("name"));
        if (!hasText(toolName)) {
            throw new InvalidRequestException("tools/call requires 'name'.");
        }

        JsonNode arguments = params.get("arguments");
        if (arguments != null && !arguments.isObject()) {
            throw new InvalidRequestException("'arguments' must be an object.");
        }

        return mcpToolService.callTool(toolName, arguments);
    }

    private ObjectNode jsonRpcSuccess(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSON_RPC_VERSION);
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode jsonRpcError(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSON_RPC_VERSION);
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private JsonNode normalizeId(JsonNode id) {
        return id == null ? NullNode.instance : id;
    }

    private String asText(JsonNode node) {
        return node != null && !node.isNull() ? node.asText() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
