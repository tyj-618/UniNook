package com.uninook.ai;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolCallExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolSecurityValidator toolSecurityValidator;

    public ToolCallExecutor(ToolRegistry toolRegistry, ToolSecurityValidator toolSecurityValidator) {
        this.toolRegistry = toolRegistry;
        this.toolSecurityValidator = toolSecurityValidator;
    }

    public ToolExecutionResult execute(ToolCall toolCall, ToolExecutionContext context) {
        AgentTool tool = toolRegistry.find(toolCall.name())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "不允许调用该工具"));
        if (tool.definition().operation() == ToolOperation.WRITE) {
            Map<String, Object> safeArguments = toolSecurityValidator.validateAndSecure(
                    tool.definition(), toolCall.argumentsJson(), context);
            return ToolExecutionResult.pendingConfirmation(tool.pendingConfirmationMessage(context, safeArguments));
        }
        Map<String, Object> safeArguments = toolSecurityValidator.validateAndSecure(
                tool.definition(), toolCall.argumentsJson(), context);
        return tool.execute(context, safeArguments);
    }
}
