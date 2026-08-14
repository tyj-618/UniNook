package com.uninook.ai;

import java.util.List;

/**
 * Result of the tool-planning stage before the assistant sends its final response.
 */
public record AgentStreamingPlan(String immediateAnswer, List<ChatMessage> finalMessages,
                                 List<AiPostReference> references, boolean pendingConfirmation) {

    public AgentStreamingPlan {
        finalMessages = finalMessages == null ? List.of() : List.copyOf(finalMessages);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public boolean requiresModelStream() {
        return immediateAnswer == null;
    }

    static AgentStreamingPlan stream(List<ChatMessage> finalMessages, List<AiPostReference> references) {
        return new AgentStreamingPlan(null, finalMessages, references, false);
    }

    static AgentStreamingPlan immediate(AgentResult result) {
        return new AgentStreamingPlan(result.answer(), List.of(), result.references(), result.pendingConfirmation());
    }
}
