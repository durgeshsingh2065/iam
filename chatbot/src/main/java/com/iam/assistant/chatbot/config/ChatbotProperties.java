package com.iam.assistant.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "chatbot")
public record ChatbotProperties(
        SessionProperties session,
        AuthProperties auth,
        StorageProperties storage
) {
    public record SessionProperties(
            @DefaultValue("30") int maxHistory,
            @DefaultValue("60") int ttlMinutes,
            @DefaultValue("20") int maxSessionsPerUser
    ) {}

    public record AuthProperties(
            @DefaultValue("admin") String adminUsername,
            @DefaultValue("admin123") String adminPassword
    ) {}

    public record StorageProperties(@DefaultValue("./chatbot-data") String path) {}
}
