package com.iam.assistant.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iam.assistant.agent.model.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Communicates with the MCP Server using the JSON-RPC 2.0 protocol.
 *
 * Responsibilities:
 *   1. Initialize the session with the MCP Server (capability handshake)
 *   2. Fetch tool definitions for Claude
 *   3. Execute tool calls on behalf of the agent loop
 */
@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final WebClient mcpWebClient;
    private final ObjectMapper objectMapper;

    private final AtomicInteger requestIdSeq = new AtomicInteger(1);

    public McpClientService(@Qualifier("mcpWebClient") WebClient mcpWebClient,
                            ObjectMapper objectMapper) {
        this.mcpWebClient = mcpWebClient;
        this.objectMapper = objectMapper;
    }

    // ── Initialize ────────────────────────────────────────────────────

    public JsonNode initialize() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.putObject("clientInfo")
                .put("name", "iam-agent")
                .put("version", "1.0.0");

        return callMcp(buildRequest("initialize", params), null);
    }

    // ── Tools ─────────────────────────────────────────────────────────

    /**
     * Fetches all tool definitions from the MCP Server.
     * Returns the list in the format Claude's API expects.
     */
    public List<Map<String, Object>> listTools() {
        ObjectNode request = buildRequest("tools/list", objectMapper.createObjectNode());
        JsonNode response  = callMcp(request, null);
        JsonNode toolsNode = response.path("result").path("tools");

        return objectMapper.convertValue(toolsNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
    }

    /**
     * Executes a tool on the MCP Server.
     *
     * @param toolName  the tool's snake_case name
     * @param arguments JSON arguments from Claude's tool_use block
     * @param credentials the chatbot user's SailPoint login, or null to let the MCP Server use its own
     * @return the tool result as a JsonNode
     */
    public JsonNode callTool(String toolName, JsonNode arguments, UserCredentials credentials) {
        ObjectNode params = objectMapper.createObjectNode()
                .put("name", toolName);
        params.set("arguments", arguments);

        ObjectNode request = buildRequest("tools/call", params);
        JsonNode response  = callMcp(request, credentials);

        log.debug("Tool {} result: {}", toolName,
                response.path("result").path("content").path(0).path("text").asText(""));

        return response.path("result");
    }

    // ── Internal ──────────────────────────────────────────────────────

    private ObjectNode buildRequest(String method, ObjectNode params) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", requestIdSeq.getAndIncrement());
        req.put("method", method);
        req.set("params", params);
        return req;
    }

    private JsonNode callMcp(JsonNode requestBody, UserCredentials credentials) {
        log.debug("MCP call: {}", requestBody.path("method").asText());
        try {
            WebClient.RequestBodySpec spec = mcpWebClient.post()
                    .uri("/mcp");
            if (credentials != null) {
                // Only the SailPoint tools read this header; the MCP Server falls back to its own auth without it.
                spec = spec.header("X-SailPoint-Authorization", credentials.toBasicAuthorization());
            }
            return spec
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception ex) {
            log.error("MCP Server call failed: {}", ex.getMessage());
            throw new McpCommunicationException("MCP Server unreachable: " + ex.getMessage(), ex);
        }
    }

    public static class McpCommunicationException extends RuntimeException {
        private static final long serialVersionUID = 6129981327979614720L;

		public McpCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
