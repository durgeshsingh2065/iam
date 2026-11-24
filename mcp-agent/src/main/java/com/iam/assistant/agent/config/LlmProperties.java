package com.iam.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** anthropic | openai | ollama | azure-openai | mistral | deepseek | minimax | zhipuai | bedrock | vertex-ai */
    private String provider = "anthropic";
    private String systemPromptPath = "";
    /** Maximum number of MCP tool calls the model may make while answering one query. */
    private int maxToolIterations = 12;
    /**
     * Skip TLS certificate-chain verification for LLM endpoints.
     * Only for hosts whose certificate is not in the JVM truststore, e.g. an on-prem gateway.
     */
    private boolean trustAllCerts = false;

    /** Settings for the providers beyond anthropic/openai/ollama, keyed by provider name. */
    private Map<String, ProviderProperties> providers = new HashMap<>();

    public Map<String, ProviderProperties> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderProperties> providers) { this.providers = providers; }

    public ProviderProperties provider(String name) {
        ProviderProperties p = providers.get(name);
        if (p == null) {
            throw new IllegalStateException("No settings found under llm.providers." + name);
        }
        return p;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getSystemPromptPath() { return systemPromptPath; }
    public void setSystemPromptPath(String systemPromptPath) { this.systemPromptPath = systemPromptPath; }

    public int getMaxToolIterations() { return maxToolIterations; }
    public void setMaxToolIterations(int maxToolIterations) { this.maxToolIterations = maxToolIterations; }

    public boolean isTrustAllCerts() { return trustAllCerts; }
    public void setTrustAllCerts(boolean trustAllCerts) { this.trustAllCerts = trustAllCerts; }
}
