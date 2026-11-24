package com.iam.assistant.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.assistant.agent.model.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/** Exposes one MCP Server tool to Spring AI, which runs the tool-calling loop. */
public class McpToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(McpToolCallback.class);

    private static final String LIMIT_MESSAGE =
            "Tool call limit reached. Using only the information already retrieved, provide the "
            + "best answer you can. If the information is insufficient, say so clearly.";

    private final ToolDefinition definition;
    private final McpClientService mcpClientService;
    private final ObjectMapper objectMapper;
    private final ToolRun run;
    private final UserCredentials credentials;

    public McpToolCallback(ToolDefinition definition,
                           McpClientService mcpClientService,
                           ObjectMapper objectMapper,
                           ToolRun run,
                           UserCredentials credentials) {
        this.definition = definition;
        this.mcpClientService = mcpClientService;
        this.objectMapper = objectMapper;
        this.run = run;
        this.credentials = credentials;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        String name = definition.name();
        if (!run.tryAcquire(name)) {
            log.warn("Tool call budget exhausted; refusing {}", name);
            return LIMIT_MESSAGE;
        }
        try {
            log.info("Executing tool: {}", name);
            JsonNode args = (toolInput == null || toolInput.isBlank())
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(toolInput);
            return extractText(mcpClientService.callTool(name, args, credentials));
        } catch (Exception ex) {
            log.error("Tool {} execution failed: {}", name, ex.getMessage());
            return "Tool error: " + ex.getMessage();
        }
    }

    private String extractText(JsonNode mcpResult) {
        JsonNode contentArr = mcpResult.path("content");
        if (contentArr.isArray() && !contentArr.isEmpty()) {
            return contentArr.get(0).path("text").asText("");
        }
        return mcpResult.toPrettyString();
    }
}
