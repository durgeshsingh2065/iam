package com.iam.assistant.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentRequest {

    /** The user's natural-language query */
    @NotBlank(message = "query must not be blank")
    private String query;

    /**
     * Optional: conversation history for multi-turn support.
     * Each entry has role ("user"|"assistant") and content.
     */
    private List<ConversationTurn> history;

    /** Optional session ID for context tracking */
    private String sessionId;

    /** Caller's API key forwarded by the chatbot; used to authorize LLM calls. */
    private String userApiKey;

    /** SailPoint login of the chatbot user. Optional: when absent the MCP server uses its own configured auth. */
    private String sailpointUsername;

    /** SailPoint password of the chatbot user. Optional; forwarded to the MCP server, never logged. */
    private String sailpointPassword;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public List<ConversationTurn> getHistory() { return history; }
    public void setHistory(List<ConversationTurn> history) { this.history = history; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserApiKey() { return userApiKey; }
    public void setUserApiKey(String userApiKey) { this.userApiKey = userApiKey; }

    public String getSailpointUsername() { return sailpointUsername; }
    public void setSailpointUsername(String sailpointUsername) { this.sailpointUsername = sailpointUsername; }

    public String getSailpointPassword() { return sailpointPassword; }
    public void setSailpointPassword(String sailpointPassword) { this.sailpointPassword = sailpointPassword; }
}
