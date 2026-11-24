package com.iam.assistant.chatbot.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private String sessionId;
    private String reply;
    private List<String> toolsUsed;
    private boolean error = false;
    private String errorMessage;

    private ChatResponse() {}

    public static ChatResponse success(String reply, String sessionId, List<String> toolsUsed) {
        ChatResponse r = new ChatResponse();
        r.reply = reply;
        r.sessionId = sessionId;
        r.toolsUsed = toolsUsed;
        return r;
    }

    public static ChatResponse error(String message, String sessionId) {
        ChatResponse r = new ChatResponse();
        r.error = true;
        r.errorMessage = message;
        r.reply = "Sorry, I encountered an error: " + message;
        r.sessionId = sessionId;
        return r;
    }

    public String getSessionId() { return sessionId; }
    public String getReply() { return reply; }
    public List<String> getToolsUsed() { return toolsUsed; }
    public boolean isError() { return error; }
    public String getErrorMessage() { return errorMessage; }
}
