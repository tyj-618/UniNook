package com.uninook.ai;

import java.util.List;

public record AiModelRequest(
        String systemPrompt,
        String userPrompt,
        List<ChatMessage> messages
) {

    public AiModelRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt),
                new ChatMessage(ChatMessage.Role.USER, userPrompt)
        ));
    }

    public AiModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
