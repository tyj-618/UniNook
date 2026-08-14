package com.uninook.ai;

import java.util.Map;

public interface AgentTool {

    ToolDefinition definition();

    ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments);

    default String pendingConfirmationMessage(ToolExecutionContext context, Map<String, Object> arguments) {
        return "待确认动作：" + definition().name() + "。当前不会执行任何写入操作。";
    }

    default ToolExecutionResult preparePendingConfirmation(ToolExecutionContext context, Map<String, Object> arguments) {
        return ToolExecutionResult.pendingConfirmation(pendingConfirmationMessage(context, arguments));
    }
}
