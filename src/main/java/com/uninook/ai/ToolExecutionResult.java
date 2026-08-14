package com.uninook.ai;

import java.util.List;

public record ToolExecutionResult(String content, boolean pendingConfirmation, List<AiPostReference> references,
                                  PendingActionSummary pendingAction) {

    public ToolExecutionResult {
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static ToolExecutionResult observation(String content) {
        return new ToolExecutionResult(content, false, List.of(), null);
    }

    public static ToolExecutionResult observation(String content, List<AiPostReference> references) {
        return new ToolExecutionResult(content, false, references, null);
    }

    public static ToolExecutionResult pendingConfirmation(String content) {
        return new ToolExecutionResult(content, true, List.of(), null);
    }

    public static ToolExecutionResult pendingConfirmation(String content, PendingActionSummary pendingAction) {
        return new ToolExecutionResult(content, true, List.of(), pendingAction);
    }
}
