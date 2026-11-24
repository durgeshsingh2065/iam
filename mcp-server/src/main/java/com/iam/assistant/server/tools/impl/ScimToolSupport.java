package com.iam.assistant.server.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import org.slf4j.Logger;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Supplier;

/** Shared error handling and helpers for the SCIM tools. */
final class ScimToolSupport {

    private ScimToolSupport() {}

    static ToolCallResult call(Logger log, Supplier<ToolCallResult> action) {
        try {
            return action.get();
        } catch (WebClientResponseException ex) {
            int code = ex.getStatusCode().value();
            log.warn("SailPoint SCIM returned HTTP {}", code);
            return ToolCallResult.error(switch (code) {
                case 400 -> "SailPoint rejected the request (HTTP 400) - the filter is probably invalid.";
                case 401, 403 -> "SailPoint rejected the MCP server's credentials (HTTP " + code + ").";
                case 404 -> "SailPoint returned 404 - the configured SCIM endpoint is wrong.";
                default -> "SailPoint returned HTTP " + code + ".";
            });
        } catch (WebClientRequestException ex) {
            log.warn("Could not reach SailPoint: {}", ex.getMessage());
            return ToolCallResult.error("Could not reach SailPoint: " + ex.getMessage());
        }
    }

    /** Text of a node, or null when missing/null/blank/container. */
    static String text(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || n.isContainerNode()) return null;
        String s = n.asText();
        return s.isBlank() ? null : s;
    }

    static void line(StringBuilder sb, String label, String value) {
        if (value != null) sb.append("  ").append(label).append(": ").append(value).append("\n");
    }

    static String header(JsonNode body, int shown) {
        int total = body.path("totalResults").asInt(shown);
        int start = body.path("startIndex").asInt(1);
        return total + " total result(s); showing " + shown + " starting at " + start + "\n\n";
    }
}
