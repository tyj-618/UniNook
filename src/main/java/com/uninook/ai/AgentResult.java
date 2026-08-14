package com.uninook.ai;

import java.util.List;

public record AgentResult(String answer, String requestId, boolean pendingConfirmation,
                          List<AiPostReference> references) {

    public AgentResult {
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static AgentResult answer(String answer, String requestId, List<AiPostReference> references) {
        return new AgentResult(answer, requestId, false, references);
    }

    public static AgentResult pendingConfirmation(String answer, String requestId, List<AiPostReference> references) {
        return new AgentResult(answer, requestId, true, references);
    }
}
