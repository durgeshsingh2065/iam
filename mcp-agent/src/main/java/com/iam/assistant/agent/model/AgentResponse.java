package com.iam.assistant.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    /** Claude's final natural-language answer */
    private String answer;

    /** Session ID echoed back for stateful clients */
    private String sessionId;

    /** Tools that were called during this agent turn (for transparency) */
    private List<String> toolsUsed;

    /** Whether an error occurred */
    private boolean error = false;

    private String errorMessage;

    private AgentResponse() {}

    // ── Factories ─────────────────────────────────────────────────────

    public static AgentResponse success(String answer, String sessionId, List<String> toolsUsed) {
        AgentResponse r = new AgentResponse();
        r.answer = answer;
        r.sessionId = sessionId;
        r.toolsUsed = toolsUsed;
        return r;
    }

    public static AgentResponse error(String message) {
        AgentResponse r = new AgentResponse();
        r.error = true;
        r.errorMessage = message;
        r.answer = "I encountered an error: " + message;
        return r;
    }

    public String getAnswer() { return answer; }
    public String getSessionId() { return sessionId; }
    public List<String> getToolsUsed() { return toolsUsed; }
    public boolean isError() { return error; }
    public String getErrorMessage() { return errorMessage; }
}
