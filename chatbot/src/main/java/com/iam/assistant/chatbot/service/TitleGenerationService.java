package com.iam.assistant.chatbot.service;

import org.springframework.stereotype.Service;

@Service
public class TitleGenerationService {

    private static final int MAX_TITLE_LENGTH = 60;

    public String generateTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) return "New Chat";

        String cleaned = firstMessage.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^\\w\\s\\-?!]", "");

        if (cleaned.isBlank()) return "New Chat";
        if (cleaned.length() <= MAX_TITLE_LENGTH) return cleaned;

        // Truncate at word boundary
        String truncated = cleaned.substring(0, MAX_TITLE_LENGTH);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) truncated = truncated.substring(0, lastSpace);

        truncated = truncated.stripTrailing();
        return truncated.isBlank() ? "New Chat" : truncated;
    }
}
