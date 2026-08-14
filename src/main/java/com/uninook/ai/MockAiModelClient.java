package com.uninook.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "campuscircle.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiModelClient implements AiModelClient {

    private static final Pattern POST_ID_PATTERN = Pattern.compile("postId: (\\d+)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("<question>\\s*(.*?)\\s*</question>", Pattern.DOTALL);
    private final AtomicReference<List<ChatMessage>> lastGeneratedMessages = new AtomicReference<>(List.of());
    private final ConcurrentLinkedDeque<AgentModelResponse> scriptedToolResponses = new ConcurrentLinkedDeque<>();
    private final List<List<ChatMessage>> toolRequestHistory = new CopyOnWriteArrayList<>();

    @Override
    public AiModelResult generate(List<ChatMessage> messages) {
        List<ChatMessage> copiedMessages = List.copyOf(messages);
        lastGeneratedMessages.set(copiedMessages);
        String currentUserPrompt = copiedMessages.stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .reduce((ignored, latest) -> latest)
                .map(ChatMessage::content)
                .orElse("");
        Matcher matcher = POST_ID_PATTERN.matcher(currentUserPrompt);
        List<Long> postIds = matcher.results()
                .map(match -> Long.parseLong(match.group(1)))
                .limit(3)
                .toList();
        return new AiModelResult(
                "已找到相关校园帖子，请查看下方引用内容以了解详情。",
                postIds,
                postIds.isEmpty(),
                UUID.randomUUID().toString(),
                0,
                0
        );
    }

    @Override
    public void generateStream(List<ChatMessage> messages, AiStreamChunkConsumer chunkConsumer)
            throws java.io.IOException {
        lastGeneratedMessages.set(List.copyOf(messages));
        String answer = "已找到相关校园帖子，请查看下方参考内容以了解详情。";
        int chunkSize = 8;
        for (int start = 0; start < answer.length(); start += chunkSize) {
            chunkConsumer.accept(answer.substring(start, Math.min(start + chunkSize, answer.length())));
        }
    }

    @Override
    public AgentModelResponse generateWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        List<ChatMessage> copiedMessages = List.copyOf(messages);
        lastGeneratedMessages.set(copiedMessages);
        toolRequestHistory.add(copiedMessages);
        AgentModelResponse scriptedResponse = scriptedToolResponses.pollFirst();
        if (scriptedResponse != null) {
            return scriptedResponse;
        }
        boolean hasToolObservation = copiedMessages.stream().anyMatch(message -> message.role() == ChatMessage.Role.TOOL);
        if (!hasToolObservation) {
            String userMessage = copiedMessages.stream()
                    .filter(message -> message.role() == ChatMessage.Role.USER)
                    .reduce((ignored, latest) -> latest)
                    .map(ChatMessage::content)
                    .orElse("campus information");
            Matcher questionMatcher = QUESTION_PATTERN.matcher(userMessage);
            String keyword = questionMatcher.find() ? questionMatcher.group(1).trim() : userMessage;
            return new AgentModelResponse("", List.of(new ToolCall(
                    "mock-search-1", "search_posts", "{\"keyword\":\"" + escapeJson(keyword) + "\",\"user_id\":99999}")),
                    UUID.randomUUID().toString());
        }
        return new AgentModelResponse("已根据校园帖子检索结果整理了可参考的信息。", List.of(), UUID.randomUUID().toString());
    }

    List<ChatMessage> lastGeneratedMessages() {
        return lastGeneratedMessages.get();
    }

    void scriptToolResponses(AgentModelResponse... responses) {
        scriptedToolResponses.clear();
        java.util.Collections.addAll(scriptedToolResponses, responses);
        toolRequestHistory.clear();
    }

    List<List<ChatMessage>> toolRequestHistory() {
        return List.copyOf(toolRequestHistory);
    }

    @Override
    public AiTextResult generateText(AiModelRequest request) {
        return new AiTextResult(
                "{\"score\":50,\"verdict\":\"UNCERTAIN\",\"reason\":\"模拟模式未调用真实模型。\"}",
                UUID.randomUUID().toString(), 0, 0
        );
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
