package com.uninook.ai;

import java.util.Map;

public interface AgentTool {

    ToolDefinition definition();

    ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments);
}
