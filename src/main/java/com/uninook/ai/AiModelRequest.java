package com.uninook.ai;

public record AiModelRequest(
        String systemPrompt,
        String userPrompt
) {
}
