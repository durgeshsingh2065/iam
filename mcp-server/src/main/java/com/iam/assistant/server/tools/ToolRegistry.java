package com.iam.assistant.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import com.iam.assistant.server.model.mcp.ToolDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final List<McpTool> tools;
    private Map<String, McpTool> toolMap;

    public ToolRegistry(List<McpTool> tools) {
        this.tools = tools;
    }

    @PostConstruct
    public void init() {
        toolMap = tools.stream()
                .collect(Collectors.toMap(McpTool::getName, Function.identity()));
        log.info("MCP ToolRegistry initialized with {} tools: {}",
                toolMap.size(), toolMap.keySet());
    }

    public List<ToolDefinition> listDefinitions() {
        return tools.stream()
                .map(McpTool::getDefinition)
                .toList();
    }

    public ToolCallResult executeTool(String name, JsonNode arguments) {
        McpTool tool = toolMap.get(name);
        if (tool == null) {
            return ToolCallResult.error("Unknown tool: " + name);
        }
        try {
            return tool.execute(arguments);
        } catch (Exception ex) {
            log.error("Tool {} threw an unexpected exception", name, ex);
            return ToolCallResult.error("Tool execution failed: " + ex.getMessage());
        }
    }

    public Optional<McpTool> find(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }
}
