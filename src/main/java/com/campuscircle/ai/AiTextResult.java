package com.campuscircle.ai;

/**
 * A short, provider-generated text response for bounded AI assistance flows.
 */
public record AiTextResult(
        String content,
        String requestId,
        Integer inputTokens,
        Integer outputTokens
) {
}
