package com.uninook.ai;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        ToolOperation operation
) {
    public ToolDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
