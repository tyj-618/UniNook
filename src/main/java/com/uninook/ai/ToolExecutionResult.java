package com.uninook.ai;

import java.util.List;

public record ToolExecutionResult(String content, boolean pendingConfirmation, List<AiPostReference> references) {

    public ToolExecutionResult {
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static ToolExecutionResult observation(String content) {
        return new ToolExecutionResult(content, false, List.of());
    }

    public static ToolExecutionResult observation(String content, List<AiPostReference> references) {
        return new ToolExecutionResult(content, false, references);
    }

    public static ToolExecutionResult pendingConfirmation(String content) {
        return new ToolExecutionResult(content, true, List.of());
    }
}
