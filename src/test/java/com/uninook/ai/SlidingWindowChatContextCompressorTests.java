package com.uninook.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowChatContextCompressorTests {

    @Test
    void retainsTheLatestCompleteTopicWhenOnlyFollowUpsRemainInTheWindow() {
        AiProperties properties = new AiProperties();
        properties.setChatSessionMaxMessages(4);
        SlidingWindowChatContextCompressor compressor = new SlidingWindowChatContextCompressor(properties);
        List<ChatMessage> compressed = compressor.compress(List.of(
                new ChatMessage(ChatMessage.Role.USER, "\u4e5d\u9f99\u6e56\u6821\u533a\u81ea\u4e60\u5ba4\u5f00\u653e\u5417\uff1f"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "\u5df2\u5f00\u653e\u3002"),
                new ChatMessage(ChatMessage.Role.USER, "\u5de5\u4f5c\u65e5\u5462\uff1f"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "23:00\u3002"),
                new ChatMessage(ChatMessage.Role.USER, "\u5468\u672b\u5462\uff1f"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "22:00\u3002")));

        assertThat(compressed).hasSize(4);
        assertThat(compressed.get(0).content()).isEqualTo("\u4e5d\u9f99\u6e56\u6821\u533a\u81ea\u4e60\u5ba4\u5f00\u653e\u5417\uff1f");
        assertThat(compressed).extracting(ChatMessage::content).contains("\u5468\u672b\u5462\uff1f", "22:00\u3002");
    }
}
