package com.iam.assistant.server.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iam.assistant.server.config.SailPointScimProperties;
import com.iam.assistant.server.model.mcp.ToolCallResult;
import com.iam.assistant.server.model.mcp.ToolDefinition;
import com.iam.assistant.server.service.SailPointScimClient;
import com.iam.assistant.server.tools.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.iam.assistant.server.tools.impl.ScimToolSupport.call;

/** Read-only generic list over the SailPoint SCIM resources (Accounts, Roles, Applications, ...). */
@Component
public class SailPointScimListTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(SailPointScimListTool.class);

    private final SailPointScimProperties props;
    private final ObjectMapper objectMapper;
    private final SailPointScimClient client;

    public SailPointScimListTool(SailPointScimProperties props, ObjectMapper objectMapper, SailPointScimClient client) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public String getName() { return "scim_list_resources"; }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode p = schema.putObject("properties");
        ScimResourceCatalog.resourceProperty(p);
        ScimResourceCatalog.commonQueryProperties(p, true);
        schema.putArray("required").add("resource");

        return ToolDefinition.builder()
                .name(getName())
                .description("""
                    List resources from SailPoint (SCIM 2.0), live, as JSON. resource is one of: Accounts,
                    Alerts, Applications, Entitlements, LaunchedWorkflows, ObjectConfigs, PolicyViolations,
                    ResourceTypes, Roles, Schemas, ServiceProviderConfig, TaskResults, Users, Workflows.
                    Use for questions about roles, applications, policy violations, task results, workflows,
                    accounts, alerts or the SCIM schema. Supports SCIM filter, sorting and paging. Read-only. Page with startIndex and count to retrieve more than one page.
                    """)
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolCallResult execute(JsonNode args) {
        if (!client.isConfigured()) {
            return ToolCallResult.error("The SailPoint SCIM integration is not configured: no base URL, or no credentials were supplied and none are set on the MCP server.");
        }
        String resource = ScimResourceCatalog.resolve(args.path("resource").asText(""));
        if (resource == null) {
            return ToolCallResult.error("Unknown resource. Use one of: "
                    + String.join(", ", ScimResourceCatalog.RESOURCES.values()));
        }

        Map<String, String> q = new LinkedHashMap<>();
        if (!ScimResourceCatalog.NO_QUERY.contains(resource)) {
            for (String k : new String[]{"filter", "sortBy", "sortOrder", "attributes", "excludedAttributes"}) {
                ScimResourceCatalog.put(q, args, k);
            }
            if (ScimResourceCatalog.LOOKUP_BY_NAME.contains(resource) && args.hasNonNull("lookupByName")) {
                q.put("lookupByName", String.valueOf(args.path("lookupByName").asBoolean(false)));
            }
            if (args.hasNonNull("startIndex")) q.put("startIndex", String.valueOf(Math.max(1, args.path("startIndex").asInt(1))));
            int count = args.hasNonNull("count")
                    ? Math.max(1, Math.min(args.path("count").asInt(props.getMaxResults()), props.getMaxResults()))
                    : props.getMaxResults();
            q.put("count", String.valueOf(count));
        }

        log.info("scim_list_resources: resource={} query={}", resource, q.keySet());
        return call(log, () -> {
            JsonNode body = client.get(props.getBasePath(), resource, null, q);
            if (body == null) return ToolCallResult.text("SailPoint returned an empty response.");
            // Never hand the LLM more records than configured, even if the server ignores count.
            JsonNode res = body.path("Resources");
            if (res.isArray() && res.size() > props.getMaxResults() && body instanceof ObjectNode on) {
                ArrayNode trimmed = objectMapper.createArrayNode();
                for (int i = 0; i < props.getMaxResults(); i++) trimmed.add(res.get(i));
                on.set("Resources", trimmed);
            }
            return ToolCallResult.text("=== SAILPOINT " + resource.toUpperCase() + " ===\n"
                    + ScimResourceCatalog.render(objectMapper, body, props.getMaxResponseChars()));
        });
    }
}
