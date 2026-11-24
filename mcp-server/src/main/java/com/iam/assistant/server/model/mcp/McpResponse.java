package com.iam.assistant.server.model.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    private String jsonrpc = "2.0";
    private Object id;
    private Object result;
    private McpError error;

    private McpResponse() {}

    public static McpResponse success(Object id, Object result) {
        McpResponse r = new McpResponse();
        r.id = id;
        r.result = result;
        return r;
    }

    public static McpResponse error(Object id, int code, String message) {
        McpResponse r = new McpResponse();
        r.id = id;
        r.error = new McpError(code, message, null);
        return r;
    }

    public String getJsonrpc() { return jsonrpc; }
    public Object getId() { return id; }
    public Object getResult() { return result; }
    public McpError getError() { return error; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpError {
        private final int code;
        private final String message;
        private final Object data;

        public static final int PARSE_ERROR      = -32700;
        public static final int INVALID_REQUEST  = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS   = -32602;
        public static final int INTERNAL_ERROR   = -32603;

        public McpError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
    }
}
