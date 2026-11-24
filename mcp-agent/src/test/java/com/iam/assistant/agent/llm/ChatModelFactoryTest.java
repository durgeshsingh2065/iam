package com.iam.assistant.agent.llm;

import com.iam.assistant.agent.config.LlmProperties;
import com.iam.assistant.agent.config.ProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatModelFactoryTest {

    private ChatModelFactory factory(String provider) {
        LlmProperties llm = new LlmProperties();
        llm.setProvider(provider);

        llm.getProviders().put("anthropic", provider("http://localhost:1", "claude", "", "", ""));
        llm.getProviders().put("openai", provider("http://localhost:1", "gpt", "", "", ""));
        llm.getProviders().put("opencode", provider("http://localhost:1", "m", "", "", ""));
        llm.getProviders().put("ollama", provider("http://localhost:1", "m", "", "", ""));
        llm.getProviders().put("azure-openai", provider("http://localhost:1", "dep", "", "", ""));
        llm.getProviders().put("mistral", provider("", "", "", "", ""));
        llm.getProviders().put("deepseek", provider("", "", "", "", ""));
        llm.getProviders().put("minimax", provider("", "", "", "", ""));
        llm.getProviders().put("zhipuai", provider("", "", "", "", ""));
        llm.getProviders().put("bedrock", provider("", "anthropic.claude-3-haiku-20240307-v1:0", "us-east-1", "", ""));
        llm.getProviders().put("vertex-ai", provider("", "", "", "proj", "us-central1"));

        return new ChatModelFactory(llm);
    }

    private ProviderProperties provider(String url, String model, String region, String project, String location) {
        ProviderProperties p = new ProviderProperties();
        p.setApiUrl(url);
        p.setModel(model);
        p.setRegion(region);
        p.setProjectId(project);
        p.setLocation(location);
        return p;
    }

	/*
	 * @Test void buildsEveryTokenProviderWithAUserToken() { for (String name : new
	 * String[]{"anthropic", "openai", "opencode", "azure-openai", "mistral",
	 * "deepseek", "minimax", "zhipuai"}) { ChatModel model =
	 * factory(name).create("user-token"); assertNotNull(model, name); } }
	 * 
	 * @Test void buildsOllamaWithoutToken() {
	 * assertNotNull(factory("ollama").create(null)); }
	 * 
	 * @Test void buildsBedrockFromAwsKeyPair() {
	 * assertNotNull(factory("bedrock").create("AKIAEXAMPLE:secret"));
	 * assertNotNull(factory("bedrock").create("AKIAEXAMPLE:secret:session")); }
	 * 
	 * @Test void rejectsMalformedBedrockToken() {
	 * assertThrows(IllegalStateException.class, () ->
	 * factory("bedrock").create("not-a-key-pair")); }
	 * 
	 * @Test void missingTokenIsReportedForTokenProviders() {
	 * assertThrows(IllegalStateException.class, () ->
	 * factory("mistral").create(null)); }
	 * 
	 * @Test void unknownProviderListsSupportedOnes() { IllegalStateException ex =
	 * assertThrows(IllegalStateException.class, () -> factory("nope").create("x"));
	 * assertNotNull(ex.getMessage()); }
	 */
}
