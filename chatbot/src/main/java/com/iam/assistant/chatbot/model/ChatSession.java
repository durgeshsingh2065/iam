package com.iam.assistant.chatbot.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatSession {

    private final String sessionId;
    private final String userId;
    private final Instant createdAt;
    private final int maxHistory;
    private String title;
    private final List<Turn> history = new ArrayList<>();
    private Instant lastActivity;

    public ChatSession(String sessionId, String userId, int maxHistory) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.maxHistory = maxHistory;
        this.createdAt = Instant.now();
        this.lastActivity = this.createdAt;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Turn> getHistory() { return history; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }

    public void addUserTurn(String message) {
        history.add(new Turn("user", message, Instant.now()));
        lastActivity = Instant.now();
        trimIfNeeded(maxHistory);
    }

    public void addAssistantTurn(String message) {
        history.add(new Turn("assistant", message, Instant.now()));
    }

    public boolean isFirstUserMessage() {
        return history.stream().filter(t -> "user".equals(t.role())).count() == 1;
    }

    private void trimIfNeeded(int maxTurns) {
        while (history.size() > maxTurns) {
            history.remove(0);
        }
    }

    public record Turn(String role, String content, Instant timestamp) {}
}
