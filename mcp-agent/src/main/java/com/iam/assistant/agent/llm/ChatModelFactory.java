package com.iam.assistant.agent.llm;

import com.iam.assistant.agent.config.LlmProperties;
import com.iam.assistant.agent.config.ProviderProperties;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.KeyCredential;
import com.google.cloud.vertexai.VertexAI;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Builds a Spring AI {@link ChatModel} for the configured provider.
 *
 * A model is built per request because each chatbot user supplies their own API token,
 * which Spring AI otherwise fixes at startup. Construction does no network I/O.
 */
@Component
public class ChatModelFactory {

    public static final String SUPPORTED =
            "anthropic, openai, opencode, ollama, azure-openai, mistral, deepseek, minimax, zhipuai, bedrock, vertex-ai";

    private final LlmProperties llmProps;

    public ChatModelFactory(LlmProperties llmProps) {
        this.llmProps = llmProps;
    }

    public String getProviderName() {
        return llmProps.getProvider().toLowerCase();
    }

    /** @param userApiKey the caller's token; the configured key is used when blank */
    public ChatModel create(String userApiKey) {
        String key = (userApiKey == null || userApiKey.isBlank()) ? null : userApiKey.trim();
        return switch (getProviderName()) {
            case "anthropic" -> anthropic(key);
            case "openai", "opencode" -> openAiCompatible(getProviderName(), key);
            case "ollama" -> ollama();
            case "azure-openai" -> azureOpenAi(key);
            case "mistral" -> mistral(key);
            case "deepseek" -> deepSeek(key);
            case "minimax" -> miniMax(key);
            case "zhipuai" -> zhiPu(key);
            case "bedrock" -> bedrock(key);
            case "vertex-ai" -> vertexAi();
            default -> throw new IllegalStateException("Unsupported llm.provider '" + llmProps.getProvider()
                    + "'. Supported: " + SUPPORTED);
        };
    }

    private ChatModel anthropic(String userKey) {
        ProviderProperties p = llmProps.provider("anthropic");
        String apiKey = keyOf(userKey, p, "anthropic");
        // The Capgemini gateway authenticates with a Bearer token, so send it alongside x-api-key.
        RestClient.Builder rest = restClient(Duration.ofSeconds(p.getReadTimeoutSeconds()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);

        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(require(p.getApiUrl(), "anthropic", "api-url"))
                .apiKey(apiKey)
                .restClientBuilder(rest)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(require(p.getModel(), "anthropic", "model"))
                        .maxTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    /** OpenAI itself, or any OpenAI-compatible endpoint (on-prem gateway, OpenCode AI, ...). */
    private ChatModel openAiCompatible(String name, String userKey) {
        ProviderProperties p = llmProps.provider(name);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(require(p.getApiUrl(), name, "api-url"))
                .apiKey(keyOf(userKey, p, name))
                .completionsPath(orDefault(p.getCompletionsPath(), "/v1/chat/completions"))
                .restClientBuilder(restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())))
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(require(p.getModel(), name, "model"))
                        .maxTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    private ChatModel ollama() {
        ProviderProperties p = llmProps.provider("ollama");
        // CPU inference on large models can take minutes; the read timeout must allow for it.
        OllamaApi api = OllamaApi.builder()
                .baseUrl(require(p.getApiUrl(), "ollama", "api-url"))
                .restClientBuilder(restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())))
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(require(p.getModel(), "ollama", "model"))
                        .numPredict(p.getMaxTokens())
                        .build())
                .build();
    }

    // ── Other providers ────────────────────────────────────────────────────────

    private ChatModel azureOpenAi(String userKey) {
        ProviderProperties p = llmProps.provider("azure-openai");
        // api-url is the Azure resource endpoint; model is the deployment name.
        OpenAIClientBuilder client = new OpenAIClientBuilder()
                .endpoint(require(p.getApiUrl(), "azure-openai", "api-url"))
                .credential(new KeyCredential(keyOf(userKey, p, "azure-openai")));
        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(client)
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(require(p.getModel(), "azure-openai", "model"))
                        .maxTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    private ChatModel mistral(String userKey) {
        ProviderProperties p = llmProps.provider("mistral");
        MistralAiApi api = new MistralAiApi(
                orDefault(p.getApiUrl(), "https://api.mistral.ai"),
                keyOf(userKey, p, "mistral"),
                restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())),
                RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
        return MistralAiChatModel.builder()
                .mistralAiApi(api)
                .defaultOptions(MistralAiChatOptions.builder()
                        .model(orDefault(p.getModel(), "mistral-large-latest"))
                        .maxTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    private ChatModel deepSeek(String userKey) {
        ProviderProperties p = llmProps.provider("deepseek");
        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(orDefault(p.getApiUrl(), "https://api.deepseek.com"))
                .apiKey(keyOf(userKey, p, "deepseek"))
                .restClientBuilder(restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())))
                .build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(orDefault(p.getModel(), "deepseek-chat"))
                        .maxTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    private ChatModel miniMax(String userKey) {
        ProviderProperties p = llmProps.provider("minimax");
        MiniMaxApi api = new MiniMaxApi(
                orDefault(p.getApiUrl(), "https://api.minimax.chat"),
                keyOf(userKey, p, "minimax"),
                restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())));
        return new MiniMaxChatModel(api, MiniMaxChatOptions.builder()
                .model(orDefault(p.getModel(), MiniMaxApi.DEFAULT_CHAT_MODEL))
                .maxTokens(p.getMaxTokens())
                .build());
    }

    private ChatModel zhiPu(String userKey) {
        ProviderProperties p = llmProps.provider("zhipuai");
        ZhiPuAiApi api = new ZhiPuAiApi(
                orDefault(p.getApiUrl(), "https://open.bigmodel.cn/api/paas"),
                keyOf(userKey, p, "zhipuai"),
                restClient(Duration.ofSeconds(p.getReadTimeoutSeconds())));
        return new ZhiPuAiChatModel(api, ZhiPuAiChatOptions.builder()
                .model(orDefault(p.getModel(), ZhiPuAiApi.DEFAULT_CHAT_MODEL))
                .maxTokens(p.getMaxTokens())
                .build());
    }

    /**
     * AWS has no single bearer token, so the user token must be {@code accessKeyId:secretAccessKey}
     * or {@code accessKeyId:secretAccessKey:sessionToken}. Blank falls back to the configured
     * api-key in the same format, then to the default AWS credential chain.
     */
    private ChatModel bedrock(String userKey) {
        ProviderProperties p = llmProps.provider("bedrock");
        String creds = userKey != null ? userKey : p.getApiKey();

        BedrockProxyChatModel.Builder builder = BedrockProxyChatModel.builder()
                .timeout(Duration.ofSeconds(p.getReadTimeoutSeconds()))
                .defaultOptions(BedrockChatOptions.builder()
                        .model(require(p.getModel(), "bedrock", "model"))
                        .maxTokens(p.getMaxTokens())
                        .build());
        if (!p.getRegion().isBlank()) {
            builder.region(Region.of(p.getRegion()));
        }
        if (creds != null && !creds.isBlank()) {
            String[] parts = creds.split(":", 3);
            if (parts.length < 2) {
                throw new IllegalStateException(
                        "Bedrock token must be accessKeyId:secretAccessKey[:sessionToken]");
            }
            builder.credentialsProvider(StaticCredentialsProvider.create(parts.length == 3
                    ? AwsSessionCredentials.create(parts[0], parts[1], parts[2])
                    : AwsBasicCredentials.create(parts[0], parts[1])));
        }
        return builder.build();
    }

    /** Vertex AI authenticates with Google Application Default Credentials, not a per-user token. */
    private ChatModel vertexAi() {
        ProviderProperties p = llmProps.provider("vertex-ai");
        VertexAI vertex = new VertexAI.Builder()
                .setProjectId(require(p.getProjectId(), "vertex-ai", "project-id"))
                .setLocation(require(p.getLocation(), "vertex-ai", "location"))
                .build();
        return VertexAiGeminiChatModel.builder()
                .vertexAI(vertex)
                .defaultOptions(VertexAiGeminiChatOptions.builder()
                        .model(orDefault(p.getModel(), "gemini-2.0-flash"))
                        .maxOutputTokens(p.getMaxTokens())
                        .build())
                .build();
    }

    private static String keyOf(String userKey, ProviderProperties p, String provider) {
        String key = userKey != null ? userKey : p.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("No API token for provider '" + provider
                    + "': set one for the user or llm.providers." + provider + ".api-key");
        }
        return key;
    }

    private static String require(String value, String provider, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("llm.providers." + provider + "." + field + " is required");
        }
        return value;
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private RestClient.Builder restClient(Duration readTimeout) {
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (llmProps.isTrustAllCerts()) {
            http.sslContext(trustAllContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http.build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    private static SSLContext trustAllContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            return ctx;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not build trust-all SSL context", e);
        }
    }
}
