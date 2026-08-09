package com.campuscircle.ai;

public record AiModelRequest(
        String systemPrompt,
        String userPrompt
) {
}
