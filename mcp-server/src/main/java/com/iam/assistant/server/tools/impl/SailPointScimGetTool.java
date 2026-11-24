package com.iam.assistant.server.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Read-only fetch of one SailPoint SCIM resource by id (or name). */
@Component
public class SailPointScimGetTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(SailPointScimGetTool.class);

    private final SailPointScimProperties props;
    private final ObjectMapper objectMapper;
    private final SailPointScimClient client;

    public SailPointScimGetTool(SailPointScimProperties props, ObjectMapper objectMapper, SailPointScimClient client) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    @Override
    public String getName() { return "scim_get_resource"; }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode p = schema.putObject("properties");
        ScimResourceCatalog.resourceProperty(p);
        p.putObject("id").put("type", "string").put("description",
                "The resource id (or name when lookupByName is true). Obtain ids from scim_list_resources.");
        ScimResourceCatalog.commonQueryProperties(p, false);
        schema.putArray("required").add("resource").add("id");

        return ToolDefinition.builder()
                .name(getName())
                .description("""
                    Fetch one SailPoint (SCIM 2.0) resource by id, live, as JSON. resource is one of: Accounts,
                    Alerts, Applications, Entitlements, LaunchedWorkflows, ObjectConfigs, PolicyViolations,
                    ResourceTypes, Roles, Schemas, ServiceProviderConfig, TaskResults, Users, Workflows.
                    Use after scim_list_resources (or when the user gives an id/name) to see full detail of a
                    role, application, policy violation, task result, workflow and so on. Read-only.
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
        // ServiceProviderConfig has no id; everything else needs one.
        boolean needsId = !"ServiceProviderConfig".equals(resource);
        String id = args.path("id").asText("").trim();
        if (needsId && id.isEmpty()) {
            return ToolCallResult.error("id is required for " + resource);
        }

        Map<String, String> q = new LinkedHashMap<>();
        if (!ScimResourceCatalog.NO_QUERY.contains(resource)) {
            ScimResourceCatalog.put(q, args, "attributes");
            ScimResourceCatalog.put(q, args, "excludedAttributes");
            if (ScimResourceCatalog.LOOKUP_BY_NAME.contains(resource) && args.hasNonNull("lookupByName")) {
                q.put("lookupByName", String.valueOf(args.path("lookupByName").asBoolean(false)));
            }
        }

        log.info("scim_get_resource: resource={} id='{}'", resource, id);
        return call(log, () -> {
            JsonNode body = client.get(props.getBasePath(), resource, needsId ? id : null, q);
            if (body == null) return ToolCallResult.text("SailPoint returned an empty response.");
            return ToolCallResult.text("=== SAILPOINT " + resource.toUpperCase() + " ===\n"
                    + ScimResourceCatalog.render(objectMapper, body, props.getMaxResponseChars()));
        });
    }
}
