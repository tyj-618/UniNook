package com.uninook.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final String SEARCH_POSTS_TOOL = "search_posts";
    private static final String PREPARE_POST_TOOL = "prepare_post";
    private static final Pattern QUESTION_PATTERN = Pattern.compile("<question>\\s*(.*?)\\s*</question>", Pattern.DOTALL);
    private static final Pattern STANDALONE_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:\\u5de5\\u4f5c\\u65e5|\\u5468\\u672b|\\u51e0\\u70b9|\\u54ea\\u91cc|\\u5730\\u70b9)(?:\\u5462|\\u554a|\\u5440)?$");
    private static final Pattern SCHEDULE_FOLLOW_UP_PATTERN = Pattern.compile(
            "^(?:(?:\\u5de5\\u4f5c\\u65e5|\\u5468\\u672b)(?:\\u5f00\\u653e)?(?:\\u5230)?\\u51e0\\u70b9|\\u5f00\\u653e\\u5230\\u51e0\\u70b9)(?:\\u5462|\\u554a|\\u5440)?$");
    private static final Pattern POST_TITLE_PATTERN = Pattern.compile("标题\\s*(?:是|为)?\\s*[“\\\"']?([^”\\\"'，。]+)");
    private static final Pattern POST_CONTENT_PATTERN = Pattern.compile("内容\\s*(?:是|为)?\\s*[“\\\"']?([^”\\\"'。]+)");
    private static final String REPETITION_MESSAGE = "这一步没有新信息，请换思路或给出当前最佳答案。";
    private static final String VALIDATION_PREFIX = "工具参数校验失败：";
    private final AiModelClient aiModelClient;
    private final ToolRegistry toolRegistry;
    private final ToolCallExecutor toolCallExecutor;
    private final AiProperties properties;
    private final AiOperationalMetrics metrics;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(AiModelClient aiModelClient, ToolRegistry toolRegistry,
                             ToolCallExecutor toolCallExecutor, AiProperties properties) {
        this(aiModelClient, toolRegistry, toolCallExecutor, properties, AiOperationalMetrics.noOp());
    }

    public AgentOrchestrator(AiModelClient aiModelClient, ToolRegistry toolRegistry,
                             ToolCallExecutor toolCallExecutor, AiProperties properties,
                             AiOperationalMetrics metrics) {
        this(aiModelClient, toolRegistry, toolCallExecutor, properties, metrics, new ObjectMapper());
    }

    @Autowired
    public AgentOrchestrator(AiModelClient aiModelClient, ToolRegistry toolRegistry,
                             ToolCallExecutor toolCallExecutor, AiProperties properties,
                             AiOperationalMetrics metrics, ObjectMapper objectMapper) {
        this.aiModelClient = aiModelClient;
        this.toolRegistry = toolRegistry;
        this.toolCallExecutor = toolCallExecutor;
        this.properties = properties;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public AgentResult run(List<ChatMessage> initialMessages, ToolExecutionContext context) {
        return executeLoop(initialMessages, context).result();
    }

    /**
     * Resolves tools before opening the final model stream. If the planner can answer without a tool,
     * its answer is returned immediately to avoid a second model invocation.
     */
    public AgentStreamingPlan prepareForStreaming(List<ChatMessage> initialMessages, ToolExecutionContext context) {
        AgentLoopResult loopResult = executeLoop(initialMessages, context);
        if (loopResult.result().pendingConfirmation() || !loopResult.executedTool()) {
            return AgentStreamingPlan.immediate(loopResult.result());
        }
        log.info("assistant requestId={} stage=agent status=stream-ready references={}",
                AiRequestContext.requestId(), loopResult.result().references().size());
        return AgentStreamingPlan.stream(loopResult.messages(), loopResult.result().references());
    }

    private AgentLoopResult executeLoop(List<ChatMessage> initialMessages, ToolExecutionContext context) {
        List<ChatMessage> messages = new ArrayList<>(initialMessages);
        List<AiPostReference> references = new ArrayList<>();
        int validationFailures = 0;
        String previousFingerprint = null;
        int consecutiveRepeatCount = 0;
        boolean toolCallsDisabled = false;
        boolean executedTool = false;
        log.info("assistant requestId={} stage=agent status=started messages={} tools={}",
                AiRequestContext.requestId(), messages.size(), toolRegistry.definitions().size());
        for (int step = 0; step < properties.getAgentMaxSteps(); step++) {
            if (step == 0 && isExplicitPostPublicationRequest(latestUserQuestion(messages))
                    && toolRegistry.find(PREPARE_POST_TOOL).isPresent()) {
                ToolCall fallbackCall = fallbackPreparePostCall(messages, step + 1);
                ToolExecutionResult result = toolCallExecutor.execute(fallbackCall, context);
                log.info("assistant requestId={} stage=tool step={} tool={} status=pending-confirmation",
                        AiRequestContext.requestId(), step + 1, fallbackCall.name());
                return new AgentLoopResult(
                        AgentResult.pendingConfirmation(result.content(), null, references, result.pendingAction()), messages, true);
            }
            long modelStartedAt = System.currentTimeMillis();
            AgentModelResponse modelResponse;
            try {
                modelResponse = aiModelClient.generateWithTools(messages, toolRegistry.definitions());
            } catch (BusinessException exception) {
                metrics.recordModelCall("tools", "failed", System.currentTimeMillis() - modelStartedAt, null, null);
                throw exception;
            }
            metrics.recordModelCall("tools", "success", System.currentTimeMillis() - modelStartedAt,
                    modelResponse.inputTokens(), modelResponse.outputTokens());
            log.info("assistant requestId={} stage=agent-model step={} inputTokens={} outputTokens={} toolCalls={}",
                    AiRequestContext.requestId(), step + 1, modelResponse.inputTokens(), modelResponse.outputTokens(),
                    modelResponse.toolCalls().size());
            if (modelResponse.isFinalAnswer()) {
                if (!executedTool && !toolCallsDisabled && toolRegistry.find(SEARCH_POSTS_TOOL).isPresent()) {
                    ToolCall fallbackCall = fallbackSearchCall(messages, step + 1);
                    messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, "", null, List.of(fallbackCall)));
                    ToolExecutionResult result = toolCallExecutor.execute(fallbackCall, context);
                    executedTool = true;
                    references.addAll(result.references());
                    messages.add(new ChatMessage(ChatMessage.Role.TOOL, result.content(), fallbackCall.id()));
                    log.info("assistant requestId={} stage=tool step={} tool={} status=fallback references={}",
                            AiRequestContext.requestId(), step + 1, fallbackCall.name(), result.references().size());
                    continue;
                }
                if (modelResponse.content().isBlank()) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "智能问答服务未返回有效内容");
                }
                log.info("assistant requestId={} stage=agent status=final step={} references={}",
                        AiRequestContext.requestId(), step + 1, references.size());
                return new AgentLoopResult(AgentResult.answer(modelResponse.content(), modelResponse.requestId(), references),
                        messages, executedTool);
            }
            messages.add(new ChatMessage(ChatMessage.Role.ASSISTANT, modelResponse.content(), null, modelResponse.toolCalls()));
            for (ToolCall toolCall : modelResponse.toolCalls()) {
                log.info("assistant requestId={} stage=tool step={} tool={} status=requested",
                        AiRequestContext.requestId(), step + 1, toolCall.name());
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
                    ToolCall executableToolCall = contextualizeSearchToolCall(toolCall, messages);
                    ToolExecutionResult result = toolCallExecutor.execute(executableToolCall, context);
                    executedTool = true;
                    references.addAll(result.references());
                    if (result.pendingConfirmation()) {
                        log.info("assistant requestId={} stage=tool step={} tool={} status=pending-confirmation",
                                AiRequestContext.requestId(), step + 1, toolCall.name());
                        return new AgentLoopResult(
                                AgentResult.pendingConfirmation(result.content(), modelResponse.requestId(), references,
                                        result.pendingAction()),
                                messages, executedTool);
                    }
                    log.info("assistant requestId={} stage=tool step={} tool={} status=completed references={}",
                            AiRequestContext.requestId(), step + 1, toolCall.name(), result.references().size());
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
        return new AgentLoopResult(AgentResult.answer(
                "已达到本次查询的最大执行步数，请根据当前已获得的信息调整问题后重试。", null, references),
                messages, executedTool);
    }

    private ToolCall fallbackSearchCall(List<ChatMessage> messages, int step) {
        String keyword = contextualSearchKeyword(messages);
        String arguments = "{\"keyword\":\"" + escapeJson(keyword) + "\"}";
        return new ToolCall("fallback-search-" + step, SEARCH_POSTS_TOOL, arguments);
    }

    private ToolCall fallbackPreparePostCall(List<ChatMessage> messages, int step) {
        String question = latestUserQuestion(messages);
        String title = extractDraftField(POST_TITLE_PATTERN, question, "待确认帖子");
        String content = extractDraftField(POST_CONTENT_PATTERN, question, question);
        String arguments = "{\"title\":\"" + escapeJson(title) + "\",\"content\":\""
                + escapeJson(content) + "\"}";
        return new ToolCall("fallback-prepare-post-" + step, PREPARE_POST_TOOL, arguments);
    }

    private ToolCall contextualizeSearchToolCall(ToolCall toolCall, List<ChatMessage> messages) {
        if (!SEARCH_POSTS_TOOL.equals(toolCall.name()) || !isContextualFollowUp(latestUserQuestion(messages))) {
            return toolCall;
        }
        try {
            var arguments = objectMapper.readValue(toolCall.argumentsJson(), new TypeReference<java.util.LinkedHashMap<String, Object>>() { });
            if (!(arguments.get("keyword") instanceof String)) {
                return toolCall;
            }
            arguments.put("keyword", contextualSearchKeyword(messages));
            return new ToolCall(toolCall.id(), toolCall.name(), objectMapper.writeValueAsString(arguments));
        } catch (JsonProcessingException exception) {
            return toolCall;
        }
    }

    private String contextualSearchKeyword(List<ChatMessage> messages) {
        List<String> userQuestions = messages.stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .map(ChatMessage::content)
                .map(this::extractQuestion)
                .filter(question -> !question.isBlank())
                .toList();
        String latestQuestion = userQuestions.isEmpty()
                ? "campus information"
                : userQuestions.get(userQuestions.size() - 1);
        String keyword = latestQuestion;
        if (isContextualFollowUp(latestQuestion)) {
            String topicQuestion = findLatestTopicQuestion(userQuestions);
            if (!topicQuestion.isBlank()) {
                keyword = topicQuestion + " " + latestQuestion;
            }
        }
        return keyword;
    }

    /**
     * Keeps an omitted follow-up anchored to the latest complete user question instead of to an
     * earlier omitted follow-up. For example, “那工作日呢？” should still search around
     * “九龙湖校区自习室开放吗？”, even after the user has already asked “那周末呢？”.
     */
    private String findLatestTopicQuestion(List<String> userQuestions) {
        for (int index = userQuestions.size() - 2; index >= 0; index--) {
            String question = userQuestions.get(index);
            if (!isContextualFollowUp(question)) {
                return question;
            }
        }
        return "";
    }

    private String latestUserQuestion(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .map(ChatMessage::content)
                .map(this::extractQuestion)
                .filter(question -> !question.isBlank())
                .reduce((ignored, latest) -> latest)
                .orElse("");
    }

    private String extractQuestion(String message) {
        Matcher matcher = QUESTION_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1).trim() : message.trim();
    }

    private boolean isContextualFollowUp(String question) {
        String normalized = question.replaceAll("[\\s\\p{Punct}\\x{FF0C}\\x{3002}\\x{FF1F}\\x{FF01}\\x{3001}]", "");
        return normalized.startsWith("那") || normalized.startsWith("它")
                || normalized.startsWith("具体") || normalized.startsWith("然后")
                || normalized.startsWith("这个") || normalized.startsWith("这间")
                || normalized.startsWith("这里") || normalized.startsWith("这样")
                || STANDALONE_FOLLOW_UP_PATTERN.matcher(normalized).matches()
                || SCHEDULE_FOLLOW_UP_PATTERN.matcher(normalized).matches();
    }

    private boolean isExplicitPostPublicationRequest(String question) {
        return question.contains("帖子") && (question.contains("发布")
                || question.contains("发一条") || question.contains("发个"));
    }

    private String extractDraftField(Pattern pattern, String question, String fallback) {
        Matcher matcher = pattern.matcher(question);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private record AgentLoopResult(AgentResult result, List<ChatMessage> messages, boolean executedTool) {
    }
}
