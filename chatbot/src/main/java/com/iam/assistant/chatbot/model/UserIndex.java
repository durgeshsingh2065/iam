package com.iam.assistant.chatbot.model;

import java.util.ArrayList;
import java.util.List;

public class UserIndex {

    private String userId;
    private List<SessionSummary> sessions = new ArrayList<>();

    public UserIndex() {}

    public UserIndex(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<SessionSummary> getSessions() { return sessions; }
    public void setSessions(List<SessionSummary> sessions) { this.sessions = sessions; }
}
