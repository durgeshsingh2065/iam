package com.iam.assistant.chatbot.model;

import java.time.Instant;
import java.util.List;

public class SessionDetail {

    private String sessionId;
    private String title;
    private Instant createdAt;
    private Instant lastActivity;
    private List<PersistedMessage> messages;

    public SessionDetail() {}

    public SessionDetail(String sessionId, String title, Instant createdAt,
                         Instant lastActivity, List<PersistedMessage> messages) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.lastActivity = lastActivity;
        this.messages = messages;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public List<PersistedMessage> getMessages() { return messages; }
    public void setMessages(List<PersistedMessage> messages) { this.messages = messages; }
}
