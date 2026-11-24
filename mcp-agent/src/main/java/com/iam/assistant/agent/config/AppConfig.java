package com.iam.assistant.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AppConfig {

    private final McpClientProperties mcpProps;

    public AppConfig(McpClientProperties mcpProps) {
        this.mcpProps = mcpProps;
    }

    /** WebClient pre-configured for MCP Server calls. LLM calls go through Spring AI. */
    @Bean("mcpWebClient")
    public WebClient mcpWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) mcpProps.getConnectTimeout().toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(mcpProps.getReadTimeout().toSeconds(),
                                TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(mcpProps.getUrl())
                .defaultHeader("X-MCP-API-Key", mcpProps.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
