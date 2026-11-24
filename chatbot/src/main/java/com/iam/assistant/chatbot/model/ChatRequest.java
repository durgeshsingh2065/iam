package com.iam.assistant.chatbot.model;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank(message = "message must not be blank")
    private String message;

    /** Optional — returned in the response to allow client-side session tracking */
    private String sessionId;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
