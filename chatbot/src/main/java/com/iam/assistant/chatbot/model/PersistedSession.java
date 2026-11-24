package com.iam.assistant.chatbot.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PersistedSession {

    private String sessionId;
    private String userId;
    private String title;
    private Instant createdAt;
    private Instant lastActivity;
    private List<PersistedMessage> messages = new ArrayList<>();

    public PersistedSession() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public List<PersistedMessage> getMessages() { return messages; }
    public void setMessages(List<PersistedMessage> messages) { this.messages = messages; }
}
