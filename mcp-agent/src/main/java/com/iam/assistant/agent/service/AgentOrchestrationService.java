package com.iam.assistant.agent.service;

import com.iam.assistant.agent.config.LlmProperties;
import com.iam.assistant.agent.llm.ChatModelFactory;
import com.iam.assistant.agent.model.AgentRequest;
import com.iam.assistant.agent.model.AgentResponse;
import com.iam.assistant.agent.model.ConversationTurn;
import com.iam.assistant.agent.model.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core agent flow, built on Spring AI.
 *
 *   1. Fetch tools from the MCP Server and wrap each as a Spring AI ToolCallback
 *   2. Build a ChatClient for the configured provider (caller's API token if sent, else llm.providers.<name>.api-key)
 *   3. ChatClient sends the query + tools to the LLM and runs the tool-calling loop,
 *      executing each requested tool on the MCP Server
 *   4. Return the final text answer
 */
@Service
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    // Spring AI reports HTTP failures as "<status> - <body>"; also accept "HTTP <status>".
    private static final Pattern HTTP_STATUS = Pattern.compile("(?:^|\\s)(?:HTTP\\s+)?([45]\\d{2})\\s+-\\s|HTTP\\s+([45]\\d{2})");
    private static final int MAX_ERROR_LEN = 300;

    private final ChatModelFactory chatModelFactory;
    private final SystemPromptService systemPromptService;
    private final McpClientService mcpClientService;
    private final McpToolCallbackFactory toolCallbackFactory;
    private final LlmProperties llmProperties;

    public AgentOrchestrationService(ChatModelFactory chatModelFactory,
                                     SystemPromptService systemPromptService,
                                     McpClientService mcpClientService,
                                     McpToolCallbackFactory toolCallbackFactory,
                                     LlmProperties llmProperties) {
        this.chatModelFactory = chatModelFactory;
        this.systemPromptService = systemPromptService;
        this.mcpClientService = mcpClientService;
        this.toolCallbackFactory = toolCallbackFactory;
        this.llmProperties = llmProperties;
    }

    public AgentResponse process(AgentRequest request) {
        log.info("Agent processing query [provider={}]: {}",
                chatModelFactory.getProviderName(), request.getQuery());

        try {
            List<Map<String, Object>> toolDefinitions = mcpClientService.listTools();
            log.debug("Loaded {} tools from MCP Server", toolDefinitions.size());

            ToolRun run = new ToolRun(llmProperties.getMaxToolIterations());
            List<ToolCallback> callbacks = toolCallbackFactory.create(toolDefinitions, run,
                    UserCredentials.of(request.getSailpointUsername(), request.getSailpointPassword()));

            ChatModel model;
            try {
                model = chatModelFactory.create(request.getUserApiKey());
            } catch (IllegalStateException ex) {
                // Configuration problem (unknown provider, missing token or setting): say what is wrong.
                log.warn("Agent stopped — provider not usable: {}", ex.getMessage());
                return AgentResponse.error(ex.getMessage());
            }
            ChatClient client = ChatClient.builder(model)
                    .defaultSystem(systemPromptService.getPrompt())
                    .build();

            String answer = client.prompt()
                    .messages(buildMessages(request))
                    .toolCallbacks(callbacks)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                answer = "I was unable to complete the search within the allowed number of steps. "
                        + "Please try a more specific query.";
            }
            List<String> toolsUsed = run.getToolsUsed();
            log.info("Agent completed, tools used: {}", toolsUsed);
            return AgentResponse.success(answer.trim(), request.getSessionId(), toolsUsed);

        } catch (McpClientService.McpCommunicationException ex) {
            log.error("Agent stopped — MCP Server error: {}", ex.getMessage());
            return AgentResponse.error(ex.getMessage());
        } catch (Exception ex) {
            return AgentResponse.error(describeLlmFailure(ex));
        }
    }

    private List<Message> buildMessages(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        List<ConversationTurn> history = request.getHistory();
        if (history != null) {
            for (ConversationTurn turn : history) {
                if ("user".equals(turn.getRole())) {
                    messages.add(new UserMessage(turn.getContent()));
                } else {
                    messages.add(new AssistantMessage(turn.getContent()));
                }
            }
        }
        messages.add(new UserMessage(request.getQuery()));
        return messages;
    }

    /** Maps a Spring AI / HTTP failure to a message that is safe and useful to show the user. */
    private String describeLlmFailure(Exception ex) {
        String raw = rootMessage(ex);
        Matcher m = HTTP_STATUS.matcher(raw);
        int status = m.find() ? Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2)) : 0;

        if (status == 401 || status == 403) {
            log.warn("Agent stopped — auth failure (HTTP {}): {}", status, raw);
            return "The API token was rejected by the AI service (HTTP " + status + "). "
                    + "Update it via ⚙ My Token in the header, or clear it to use the agent default.";
        }
        if (status != 0) {
            log.error("Agent stopped — LLM API error (HTTP {}): {}", status, raw);
            String clean = raw.length() > MAX_ERROR_LEN ? raw.substring(0, MAX_ERROR_LEN) + "…" : raw;
            return "LLM API error: " + clean;
        }
        log.error("Agent orchestration failed", ex);
        return "The AI service encountered an unexpected error. Please try again.";
    }

    private static String rootMessage(Throwable t) {
        // Spring AI wraps HTTP errors (and retries) in several layers; the status is in the message chain.
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && sb.length() < 2000; c = c.getCause()) {
            if (c.getMessage() != null) sb.append(c.getMessage()).append(' ');
            if (c.getCause() == c) break;
        }
        return sb.toString().trim();
    }
}
