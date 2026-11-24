package com.iam.assistant.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.iam.assistant.server.config.SailPointScimProperties;
import com.iam.assistant.server.config.SailPointScimProperties.AuthType;
import io.netty.channel.ChannelOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP client for the SailPoint SCIM APIs.
 *
 * Credentials, in order of preference:
 *   1. the calling user's own Basic credentials (see {@link SailPointCredentialContext}), if the
 *      chatbot forwarded them;
 *   2. otherwise the server's configured auth ({@code sailpoint.scim.auth-type}: BASIC or BEARER).
 */
@Component
public class SailPointScimClient {

    private final WebClient web;
    private final String defaultAuthorization;

    public SailPointScimClient(SailPointScimProperties p) {
        if (!p.hasBaseUrl()) {
            this.web = null;
            this.defaultAuthorization = null;
            return;
        }
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, p.getConnectTimeoutSeconds() * 1000)
                .responseTimeout(Duration.ofSeconds(p.getReadTimeoutSeconds()));
        this.web = WebClient.builder()
                .baseUrl(p.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.defaultAuthorization = buildDefaultAuthorization(p);
    }

    private static String buildDefaultAuthorization(SailPointScimProperties p) {
        HttpHeaders h = new HttpHeaders();
        if (p.getAuthType() == AuthType.BEARER) {
            if (p.getBearerToken().isBlank()) return null;
            h.setBearerAuth(p.getBearerToken());
        } else {
            if (p.getUsername().isBlank()) return null;
            h.setBasicAuth(p.getUsername(), p.getPassword());
        }
        return h.getFirst(HttpHeaders.AUTHORIZATION);
    }

    /** True when a call can be made right now: a base URL plus user or default credentials. */
    public boolean isConfigured() {
        return web != null && (SailPointCredentialContext.get() != null || defaultAuthorization != null);
    }

    /**
     * GET {basePath}/{resource}[/{id}] with optional query parameters. Resource must come from a
     * whitelist; id and query values are URL-encoded as URI variables.
     */
    public JsonNode get(String basePath, String resource, String id, Map<String, String> query) {
        String authorization = SailPointCredentialContext.get() != null
                ? SailPointCredentialContext.get() : defaultAuthorization;
        return web.get()
                .uri(b -> {
                    b.path(basePath).pathSegment(resource);
                    Map<String, Object> vars = new HashMap<>();
                    if (id != null) {
                        b.pathSegment("{id}");
                        vars.put("id", id);
                    }
                    query.forEach((k, v) -> {
                        b.queryParam(k, "{" + k + "}");
                        vars.put(k, v);
                    });
                    return b.build(vars);
                })
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
