package com.uninook.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "campuscircle.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiModelClient implements AiModelClient {

    private static final Pattern POST_ID_PATTERN = Pattern.compile("postId: (\\d+)");
    private final AtomicReference<List<ChatMessage>> lastGeneratedMessages = new AtomicReference<>(List.of());

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

    List<ChatMessage> lastGeneratedMessages() {
        return lastGeneratedMessages.get();
    }

    @Override
    public AiTextResult generateText(AiModelRequest request) {
        return new AiTextResult(
                "{\"score\":50,\"verdict\":\"UNCERTAIN\",\"reason\":\"模拟模式未调用真实模型。\"}",
                UUID.randomUUID().toString(), 0, 0
        );
    }
}
