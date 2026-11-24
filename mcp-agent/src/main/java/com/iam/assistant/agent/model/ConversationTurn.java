package com.iam.assistant.agent.model;

public class ConversationTurn {

    private String role;     // "user" | "assistant"
    private String content;

    public ConversationTurn() {}

    public ConversationTurn(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ConversationTurn obj = new ConversationTurn();

        public Builder role(String role) { obj.role = role; return this; }
        public Builder content(String content) { obj.content = content; return this; }

        public ConversationTurn build() { return obj; }
    }
}
