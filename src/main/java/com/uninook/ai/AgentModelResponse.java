package com.uninook.ai;

import java.util.List;

public record AgentModelResponse(String content, List<ToolCall> toolCalls, String requestId) {

    public AgentModelResponse {
        content = content == null ? "" : content.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean isFinalAnswer() {
        return toolCalls.isEmpty();
    }
}
