package com.iam.assistant.chatbot.model;

import java.time.Instant;

public class SessionSummary {

    private String sessionId;
    private String title;
    private Instant createdAt;
    private Instant lastActivity;
    private int messageCount;

    public SessionSummary() {}

    public SessionSummary(String sessionId, String title, Instant createdAt,
                          Instant lastActivity, int messageCount) {
        this.sessionId = sessionId;
        this.title = title;
        this.createdAt = createdAt;
        this.lastActivity = lastActivity;
        this.messageCount = messageCount;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
}
