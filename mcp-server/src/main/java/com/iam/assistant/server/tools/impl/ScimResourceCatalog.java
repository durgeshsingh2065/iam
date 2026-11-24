package com.iam.assistant.server.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The read-only SailPoint SCIM resources exposed through the generic scim_* tools. */
final class ScimResourceCatalog {

    private ScimResourceCatalog() {}

    /** Lower-case name -> exact SCIM path segment. Only these can ever be requested. */
    static final Map<String, String> RESOURCES = new LinkedHashMap<>();
    static {
        for (String r : List.of("Accounts", "Alerts", "Applications", "Entitlements", "LaunchedWorkflows",
                "ObjectConfigs", "PolicyViolations", "ResourceTypes", "Roles", "Schemas",
                "ServiceProviderConfig", "TaskResults", "Users", "Workflows")) {
            RESOURCES.put(r.toLowerCase(), r);
        }
    }

    /** Discovery endpoints that take no query parameters. */
    static final Set<String> NO_QUERY = Set.of("ResourceTypes", "Schemas", "ServiceProviderConfig");

    /** Resources that accept lookupByName (per the SailPoint SCIM API). */
    static final Set<String> LOOKUP_BY_NAME = Set.of("Alerts", "Applications", "ObjectConfigs", "Roles", "Users");

    static String resolve(String name) {
        return name == null ? null : RESOURCES.get(name.trim().toLowerCase());
    }

    static void resourceProperty(ObjectNode props) {
        ObjectNode r = props.putObject("resource");
        r.put("type", "string").put("description", "SCIM resource type to query.");
        ArrayNode en = r.putArray("enum");
        RESOURCES.values().forEach(en::add);
    }

    static void commonQueryProperties(ObjectNode props, boolean list) {
        if (list) {
            props.putObject("filter").put("type", "string").put("description",
                    "Optional SCIM filter, e.g. active eq true, displayName co \"Smith\". Omit to list all.");
            props.putObject("sortBy").put("type", "string").put("description", "Optional attribute to sort by.");
            props.putObject("sortOrder").put("type", "string").put("description", "Optional: ascending or descending.");
            props.putObject("startIndex").put("type", "integer").put("description", "Optional 1-based start index for paging.");
            props.putObject("count").put("type", "integer").put("description", "Optional max records to return.");
        }
        props.putObject("attributes").put("type", "string").put("description",
                "Optional comma-separated attributes to return (limits the response).");
        props.putObject("excludedAttributes").put("type", "string").put("description",
                "Optional comma-separated attributes to leave out.");
        props.putObject("lookupByName").put("type", "boolean").put("description",
                "Optional. Look up by name instead of id (Alerts, Applications, ObjectConfigs, Roles, Users only).");
    }

    /** Copies a non-blank string argument into the query map. */
    static void put(Map<String, String> q, JsonNode args, String key) {
        String v = args.path(key).asText("").trim();
        if (!v.isEmpty()) q.put(key, v);
    }

    /**
     * Drops SCIM bookkeeping that tells the LLM nothing ("schemas", "meta") so more records fit in the
     * response limit and in the model's output budget. Mutates and returns the given node.
     */
    static JsonNode compact(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            o.remove("schemas");
            o.remove("meta");
            o.elements().forEachRemaining(ScimResourceCatalog::compact);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(ScimResourceCatalog::compact);
        }
        return node;
    }

    /** Serialises to JSON, truncating (with a note) beyond maxChars. */
    static String render(ObjectMapper om, JsonNode node, int maxChars) {
        String s = compact(node).toString();
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n... [truncated: response exceeded " + maxChars
                + " characters; narrow with filter/attributes/count]";
    }
}
