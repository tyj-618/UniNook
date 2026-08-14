package com.uninook.ai;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentOrchestrator {

    private static final String REPETITION_MESSAGE = "这一步没有新信息，请换思路或给出当前最佳答案。";
    private static final String VALIDATION_PREFIX = "工具参数校验失败：";
    private final AiModelClient aiModelClient;
    private final ToolRegistry toolRegistry;
    private final ToolCallExecutor toolCallExecutor;
    private final AiProperties properties;

    public AgentOrchestrator(AiModelClient aiModelClient, ToolRegistry toolRegistry,
                             ToolCallExecutor toolCallExecutor, AiProperties properties) {
        this.aiModelClient = aiModelClient;
        this.toolRegistry = toolRegistry;
        this.toolCallExecutor = toolCallExecutor;
        this.properties = properties;
    }

    public AgentResult run(List<ChatMessage> initialMessages, ToolExecutionContext context) {
        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<AiPostReference> references = new ArrayList<>();
        int validationFailures = 0;
        String previousFingerprint = null;
        int consecutiveRepeatCount = 0;
        boolean toolCallsDisabled = false;
        for (int step = 0; step < properties.getAgentMaxSteps(); step++) {
            AgentModelResponse modelResponse = aiModelClient.generateWithTools(messages, toolRegistry.definitions());
            if (modelResponse.isFinalAnswer()) {
                if (modelResponse.content().isBlank()) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "智能问答服务未返回有效内容");
                }
                return AgentResult.answer(modelResponse.content(), modelResponse.requestId(), references);
            }
            messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, modelResponse.content(), null, modelResponse.toolCalls()));
            for (ToolCall toolCall : modelResponse.toolCalls()) {
                if (toolCallsDisabled) {
                    messages.add(new ChatMessage(ChatMessage.Role.TOOL,
                            "Tool calls are disabled after repeated validation failures. Please answer with the available information.",
                            toolCall.id()));
                    continue;
                }
                String fingerprint = toolCall.name() + ':' + toolCall.argumentsJson();
                if (fingerprint.equals(previousFingerprint)) {
                    consecutiveRepeatCount++;
                } else {
                    previousFingerprint = fingerprint;
                    consecutiveRepeatCount = 1;
                }
                if (consecutiveRepeatCount >= 2) {
                    messages.add(new ChatMessage(ChatMessage.Role.TOOL, REPETITION_MESSAGE, toolCall.id()));
                    continue;
                }
                try {
                    ToolExecutionResult result = toolCallExecutor.execute(toolCall, context);
                    references.addAll(result.references());
                    if (result.pendingConfirmation()) {
                        return AgentResult.pendingConfirmation(result.content(), modelResponse.requestId(), references);
                    }
                    messages.add(new ChatMessage(ChatMessage.Role.TOOL, result.content(), toolCall.id()));
                } catch (BusinessException exception) {
                    validationFailures++;
                    messages.add(new ChatMessage(ChatMessage.Role.TOOL,
                            VALIDATION_PREFIX + exception.getMessage(), toolCall.id()));
                    if (validationFailures >= properties.getAgentMaxValidationRetries()) {
                        toolCallsDisabled = true;
                        messages.add(new ChatMessage(ChatMessage.Role.USER,
                                "工具调用连续校验失败，请不要再调用工具，直接基于已有信息给出当前最佳答案。"));
                    }
                }
            }
        }
        return AgentResult.answer("已达到本次查询的最大执行步数，请根据当前已获得的信息调整问题后重试。", null, references);
    }
}
