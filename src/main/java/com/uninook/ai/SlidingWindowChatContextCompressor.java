package com.uninook.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlidingWindowChatContextCompressor implements ChatContextCompressor {

    private final AiProperties properties;

    public SlidingWindowChatContextCompressor(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<ChatMessage> compress(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int maxMessages = properties.getChatSessionMaxMessages();
        if (messages.size() <= maxMessages) {
            return List.copyOf(messages);
        }
        int topicIndex = ConversationContext.findLatestTopicUserMessageIndex(messages);
        int recentStart = Math.max(0, messages.size() - Math.max(0, maxMessages - 1));
        if (topicIndex < 0 || topicIndex >= recentStart) {
            return List.copyOf(messages.subList(messages.size() - maxMessages, messages.size()));
        }

        List<ChatMessage> compressed = new ArrayList<>(maxMessages);
        compressed.add(messages.get(topicIndex));
        compressed.addAll(messages.subList(recentStart, messages.size()));
        return List.copyOf(compressed);
    }
}
