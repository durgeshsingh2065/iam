package com.iam.assistant.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import com.iam.assistant.server.model.mcp.ToolDefinition;

/**
 * Contract every MCP tool must implement.
 * Tools are registered in {@link ToolRegistry} and dispatched by the MCP controller.
 */
public interface McpTool {

    /** Unique snake_case name used in tools/call requests */
    String getName();

    /** Full tool definition (name, description, JSON Schema) sent to the LLM */
    ToolDefinition getDefinition();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments parsed JSON arguments from the tools/call request
     * @return result content — never null; use {@link ToolCallResult#error} on failure
     */
    ToolCallResult execute(JsonNode arguments);
}
