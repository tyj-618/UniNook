package com.uninook.ai;

import java.util.List;

/**
 * Stable response data sent before streaming answer text.
 */
public record AiAssistantStreamMetadata(
        List<AiPostReference> references,
        boolean insufficientEvidence,
        PendingActionSummary pendingAction
) {
    public AiAssistantStreamMetadata {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
