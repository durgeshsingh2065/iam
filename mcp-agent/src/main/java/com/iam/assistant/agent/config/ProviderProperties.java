package com.iam.assistant.agent.config;

/**
 * Settings for one provider under {@code llm.providers.<name>}. Not every field applies to every
 * provider (e.g. region is Bedrock-only, project-id and location are Vertex-only).
 */
public class ProviderProperties {

    private String apiUrl = "";
    private String apiKey = "";
    /** Chat completions path for OpenAI-compatible providers (openai, opencode). */
    private String completionsPath = "";
    private String model = "";
    private int maxTokens = 4096;
    private int readTimeoutSeconds = 120;
    /** AWS region (Bedrock). Empty = default AWS region chain. */
    private String region = "";
    /** GCP project id (Vertex AI). */
    private String projectId = "";
    /** GCP location (Vertex AI), e.g. us-central1. */
    private String location = "";

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getCompletionsPath() { return completionsPath; }
    public void setCompletionsPath(String completionsPath) { this.completionsPath = completionsPath; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
