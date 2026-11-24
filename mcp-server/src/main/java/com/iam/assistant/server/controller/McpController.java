package com.iam.assistant.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iam.assistant.server.model.mcp.McpRequest;
import com.iam.assistant.server.model.mcp.McpResponse;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import com.iam.assistant.server.service.SailPointCredentialContext;
import com.iam.assistant.server.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final ToolRegistry toolRegistry;

    public McpController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public McpResponse handle(@RequestBody McpRequest request,
                              @RequestHeader(value = "X-SailPoint-Authorization", required = false)
                              String sailPointAuthorization) {
        log.debug("MCP request: method={} id={}", request.getMethod(), request.getId());

        return switch (request.getMethod()) {
            case "tools/list"   -> handleToolsList(request);
            case "tools/call"   -> handleToolsCall(request, sailPointAuthorization);
           // case "ping"         -> McpResponse.success(request.getId(), Map.of("type", "pong"));
            default             -> McpResponse.error(request.getId(),
                    McpResponse.McpError.METHOD_NOT_FOUND,
                    "Method not found: " + request.getMethod());
        };
    }

   private McpResponse handleToolsList(McpRequest request) {
        return McpResponse.success(request.getId(),
                Map.of("tools", toolRegistry.listDefinitions()));
    }

    private McpResponse handleToolsCall(McpRequest request, String sailPointAuthorization) {
        JsonNode params = request.getParams();
        if (params == null) {
            return McpResponse.error(request.getId(),
                    McpResponse.McpError.INVALID_PARAMS, "params required");
        }

        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        if (toolName.isBlank()) {
            return McpResponse.error(request.getId(),
                    McpResponse.McpError.INVALID_PARAMS, "name required in params");
        }

        log.info("Executing tool: {} with args: {}", toolName, arguments);

        // The caller's own SailPoint credentials (if any) apply to this tool call only.
        SailPointCredentialContext.set(sailPointAuthorization);
        try {
            ToolCallResult result = toolRegistry.executeTool(toolName, arguments);
            return McpResponse.success(request.getId(), result);
        } finally {
            SailPointCredentialContext.clear();
        }
    }
}
