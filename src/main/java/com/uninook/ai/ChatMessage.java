package com.uninook.ai;

import java.util.Locale;

public record ChatMessage(Role role, String content) {

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        content = content == null ? "" : content.trim();
    }

    public String providerRole() {
        return role.name().toLowerCase(Locale.ROOT);
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
