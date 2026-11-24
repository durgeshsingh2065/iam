package com.iam.assistant.server.model.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private String name;
    private String description;
    @JsonProperty("input_schema")
    private ObjectNode inputSchema;

    private ToolDefinition() {}

    public static Builder builder() { return new Builder(); }

    public String getName() { return name; }
    public String getDescription() { return description; }

    @JsonProperty("input_schema")
    public ObjectNode getInputSchema() { return inputSchema; }

    public static class Builder {
        private final ToolDefinition obj = new ToolDefinition();

        public Builder name(String name) { obj.name = name; return this; }
        public Builder description(String description) { obj.description = description; return this; }
        public Builder inputSchema(ObjectNode inputSchema) { obj.inputSchema = inputSchema; return this; }

        public ToolDefinition build() { return obj; }
    }
}
