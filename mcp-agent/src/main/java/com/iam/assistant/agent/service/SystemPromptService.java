package com.iam.assistant.agent.service;

import com.iam.assistant.agent.config.LlmProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class SystemPromptService {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptService.class);
    private static final String DEFAULT_PROMPT_RESOURCE = "classpath:default-system-prompt.txt";

    private final LlmProperties llmProperties;
    private final ResourceLoader resourceLoader;

    private volatile String currentPrompt;
    private volatile String currentSource;
    private volatile Instant lastLoaded;

    public SystemPromptService(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.resourceLoader = new DefaultResourceLoader();
    }

    @PostConstruct
    public void load() {
        doLoad();
    }

    public void reload() {
        doLoad();
    }

    public String getPrompt() {
        return currentPrompt;
    }

    public String getSource() {
        return currentSource;
    }

    public Instant getLastLoaded() {
        return lastLoaded;
    }

    private void doLoad() {
        String configuredPath = llmProperties.getSystemPromptPath();

        if (configuredPath != null && !configuredPath.isBlank()) {
            try {
                String content = readResource(configuredPath);
                currentPrompt = content;
                currentSource = configuredPath;
                lastLoaded = Instant.now();
                log.info("System prompt loaded from: {}", configuredPath);
                return;
            } catch (IOException ex) {
                log.warn("Failed to load system prompt from '{}': {} — falling back to classpath default",
                        configuredPath, ex.getMessage());
            }
        }

        try {
            String content = readResource(DEFAULT_PROMPT_RESOURCE);
            currentPrompt = content;
            currentSource = DEFAULT_PROMPT_RESOURCE;
            lastLoaded = Instant.now();
            log.info("System prompt loaded from classpath default");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load default system prompt from " + DEFAULT_PROMPT_RESOURCE, ex);
        }
    }

    private String readResource(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("Resource not found: " + location);
        }
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
