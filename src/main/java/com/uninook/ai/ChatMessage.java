package com.uninook.ai;

import java.util.List;
import java.util.Locale;

public record ChatMessage(Role role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public ChatMessage(Role role, String content) {
        this(role, content, null, List.of());
    }

    public ChatMessage(Role role, String content, String toolCallId) {
        this(role, content, toolCallId, List.of());
    }

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        content = content == null ? "" : content.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public String providerRole() {
        return role.name().toLowerCase(Locale.ROOT);
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }
}
