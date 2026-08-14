package com.uninook.ai;

import java.util.List;

public record AgentModelResponse(String content, List<ToolCall> toolCalls, String requestId,
                                 Integer inputTokens, Integer outputTokens) {

    public AgentModelResponse(String content, List<ToolCall> toolCalls, String requestId) {
        this(content, toolCalls, requestId, null, null);
    }

    public AgentModelResponse {
        content = content == null ? "" : content.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean isFinalAnswer() {
        return toolCalls.isEmpty();
    }
}
