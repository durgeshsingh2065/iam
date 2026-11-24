package com.iam.assistant.server.model.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallResult {

    private List<ContentBlock> content;
    private boolean isError = false;

    private ToolCallResult() {}

    public static ToolCallResult text(String text) {
        ToolCallResult r = new ToolCallResult();
        r.content = List.of(ContentBlock.text(text));
        return r;
    }

    public static ToolCallResult error(String message) {
        ToolCallResult r = new ToolCallResult();
        r.content = List.of(ContentBlock.text(message));
        r.isError = true;
        return r;
    }

    public List<ContentBlock> getContent() { return content; }
    public boolean isError() { return isError; }

    public static class ContentBlock {
        private String type;
        private String text;

        private ContentBlock() {}

        public static ContentBlock text(String text) {
            ContentBlock b = new ContentBlock();
            b.type = "text";
            b.text = text;
            return b;
        }

        public String getType() { return type; }
        public String getText() { return text; }
    }
}
