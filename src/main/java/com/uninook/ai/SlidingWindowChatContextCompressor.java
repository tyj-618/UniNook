package com.uninook.ai;

import org.springframework.stereotype.Component;

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
        return List.copyOf(messages.subList(messages.size() - maxMessages, messages.size()));
    }
}
