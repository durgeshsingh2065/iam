package com.iam.assistant.chatbot.service;

import com.iam.assistant.chatbot.model.ChatRequest;
import com.iam.assistant.chatbot.model.ChatResponse;
import com.iam.assistant.chatbot.model.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private final WebClient.Builder webClientBuilder;
    private final PersistentSessionService sessionService;
    private final UserManagementService userManagementService;

    @Value("${mcp.agent.url}")
    private String agentUrl;

    public ChatbotService(WebClient.Builder webClientBuilder,
                          PersistentSessionService sessionService,
                          UserManagementService userManagementService) {
        this.webClientBuilder = webClientBuilder;
        this.sessionService = sessionService;
        this.userManagementService = userManagementService;
    }

    /**
     * @param sailPointPassword the password typed at login, or null. When present, the login name and this
     *                          password are sent so SailPoint is queried as the user; otherwise the MCP
     *                          Server uses its own configured SailPoint auth.
     */
    public ChatResponse chat(ChatRequest request, String userId, String sailPointPassword) {
        ChatSession session = sessionService.getOrCreate(userId, request.getSessionId());
        session.addUserTurn(request.getMessage());

        boolean isFirst = session.isFirstUserMessage();
        log.info("Chat [user={}, session={}]: {}", userId, session.getSessionId(), request.getMessage());

        try {
            List<Map<String, String>> history = session.getHistory().stream()
                    .limit(Math.max(0, session.getHistory().size() - 1))
                    .map(t -> Map.of("role", t.role(), "content", t.content()))
                    .collect(Collectors.toList());

            Map<String, Object> agentPayload = new java.util.HashMap<>();
            agentPayload.put("query",      request.getMessage());
            agentPayload.put("sessionId",  session.getSessionId());
            agentPayload.put("history",    history);

            // The LLM API token is optional: when the user has none the agent uses its own configured key.
            String userApiKey = userManagementService.getUserToken(userId);
            if (userApiKey != null && !userApiKey.isBlank()) {
                agentPayload.put("userApiKey", userApiKey);
            }

            // SailPoint login is optional too: without it the MCP Server uses its own configured auth.
            if (sailPointPassword != null && !sailPointPassword.isEmpty()) {
                agentPayload.put("sailpointUsername", userId);
                agentPayload.put("sailpointPassword", sailPointPassword);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> agentResponse = buildAgentClient()
                    .post()
                    .uri("/agent/query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(agentPayload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (agentResponse == null) {
                return ChatResponse.error("No response from agent", session.getSessionId());
            }

            boolean isError = Boolean.TRUE.equals(agentResponse.get("error"));
            if (isError) {
                String errMsg = (String) agentResponse.getOrDefault("errorMessage", "Unknown error");
                return ChatResponse.error(errMsg, session.getSessionId());
            }

            String answer = (String) agentResponse.getOrDefault("answer", "");
            @SuppressWarnings("unchecked")
            List<String> toolsUsed = (List<String>) agentResponse.getOrDefault("toolsUsed", List.of());

            session.addAssistantTurn(answer);

            // Set title from first user message, then persist
            if (isFirst) {
                sessionService.setTitleIfAbsent(userId, session.getSessionId(), request.getMessage());
            }
            sessionService.persist(userId, session);

            return ChatResponse.success(answer, session.getSessionId(), toolsUsed);

        } catch (Exception ex) {
            log.error("Chat processing failed [session={}]", session.getSessionId(), ex);
            return ChatResponse.error(ex.getMessage(), session.getSessionId());
        }
    }

    private WebClient buildAgentClient() {
        return webClientBuilder.baseUrl(agentUrl).build();
    }
}
