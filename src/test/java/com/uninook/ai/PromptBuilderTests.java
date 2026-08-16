package com.uninook.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTests {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildsStructuredPromptWithReferencesHistoryAndJsonContract() {
        List<ChatMessage> history = List.of(
                new ChatMessage(ChatMessage.Role.USER, "图书馆几点开门"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "图书馆八点开门。"));

        AiModelRequest request = promptBuilder.build("那周末呢", List.of(new RetrievedPost(
                101L, "图书馆开放时间", "图书馆开放至晚上十点。", "示例校区", null)), history);

        assertThat(request.userPrompt())
                .contains("postId: 101")
                .contains("那周末呢")
                .contains("{\"answer\":\"...\",\"citedPostIds\":[1],\"insufficientEvidence\":false}");
        assertThat(request.messages())
                .extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER,
                        ChatMessage.Role.ASSISTANT, ChatMessage.Role.USER);
    }

    @Test
    void truncatesLongPostContentAndNormalizesNullContentInReferences() {
        String longContent = "长".repeat(700);

        AiModelRequest request = promptBuilder.build("自习室开放吗", List.of(
                new RetrievedPost(1L, "长内容标题", longContent, "示例校区", null),
                new RetrievedPost(2L, "空内容标题", null, "示例校区", null)), List.of());

        assertThat(request.userPrompt()).contains("长".repeat(600) + "...");
        assertThat(request.userPrompt()).doesNotContain("长".repeat(601));
        assertThat(request.userPrompt()).contains("postId: 2");
    }

    @Test
    void buildsStreamingPromptWithoutJsonContract() {
        AiModelRequest request = promptBuilder.buildStreaming("食堂几点关门", List.of(new RetrievedPost(
                201L, "食堂营业时间", "食堂营业到晚上九点。", "示例校区", null)), null);

        assertThat(request.userPrompt())
                .contains("食堂几点关门")
                .contains("食堂营业到晚上九点。")
                .doesNotContain("insufficientEvidence");
        assertThat(request.messages().get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
    }

    @Test
    void buildsAgentPromptWithQuestionAndOptionalHistory() {
        AiModelRequest withoutHistory = promptBuilder.buildAgent("自习室开放吗", null);
        AiModelRequest withHistory = promptBuilder.buildAgent("具体开放到几点", List.of(
                new ChatMessage(ChatMessage.Role.USER, "自习室开放吗"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "自习室已开放。")));

        assertThat(withoutHistory.userPrompt()).contains("自习室开放吗");
        assertThat(withoutHistory.messages()).hasSize(2);
        assertThat(withHistory.messages())
                .extracting(ChatMessage::role)
                .containsExactly(ChatMessage.Role.SYSTEM, ChatMessage.Role.USER,
                        ChatMessage.Role.ASSISTANT, ChatMessage.Role.USER);
        assertThat(withHistory.systemPrompt()).contains("registered tools");
    }
}
