package com.uninook.ai;

import java.util.List;

public record AiAssistantResponse(
        String answer,
        List<AiPostReference> references,
        boolean insufficientEvidence,
        String requestId
) {
}
