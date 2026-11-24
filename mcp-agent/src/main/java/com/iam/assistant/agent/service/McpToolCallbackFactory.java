package com.iam.assistant.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.assistant.agent.model.UserCredentials;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class McpToolCallbackFactory {

    private static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final McpClientService mcpClientService;
    private final ObjectMapper objectMapper;

    public McpToolCallbackFactory(McpClientService mcpClientService, ObjectMapper objectMapper) {
        this.mcpClientService = mcpClientService;
        this.objectMapper = objectMapper;
    }

    /** credentials may be null (the MCP Server then uses its own SailPoint auth). Builds callbacks from the MCP Server's tools/list entries ({name, description, input_schema}). */
    public List<ToolCallback> create(List<Map<String, Object>> mcpTools, ToolRun run, UserCredentials credentials) {
        return mcpTools.stream()
                .<ToolCallback>map(tool -> new McpToolCallback(toDefinition(tool), mcpClientService, objectMapper, run, credentials))
                .toList();
    }

    private ToolDefinition toDefinition(Map<String, Object> tool) {
        Object schema = tool.get("input_schema");
        return ToolDefinition.builder()
                .name(String.valueOf(tool.get("name")))
                .description(String.valueOf(tool.getOrDefault("description", "")))
                .inputSchema(schema != null ? objectMapper.valueToTree(schema).toString() : EMPTY_SCHEMA)
                .build();
    }
}
