package com.uninook.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "campuscircle.ai.provider=openai-compatible")
class OpenAiCompatibleModelClientContextTests {

    @Autowired
    private AiModelClient aiModelClient;

    @Test
    void loadsOpenAiCompatibleClientWithoutSendingExternalRequests() {
        assertThat(aiModelClient).isInstanceOf(OpenAiCompatibleModelClient.class);
    }

    @Test
    void usesJsonResponseFormatWithoutMaxTokensWhenStructuredOutputIsEnabled() {
        AiProperties properties = new AiProperties();
        properties.setModel("test-model");
        properties.setStructuredOutput(true);
        properties.setEnableThinking(false);
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(properties);

        Map<String, Object> requestBody = client.buildRequestBody(new AiModelRequest("system", "user"));

        assertThat(requestBody).containsEntry("response_format", Map.of("type", "json_object"));
        assertThat(requestBody).containsEntry("enable_thinking", false);
        assertThat(requestBody).doesNotContainKey("max_tokens");
    }

    @Test
    void keepsCompleteConversationMessageListInProviderRequest() {
        AiProperties properties = new AiProperties();
        properties.setModel("test-model");
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(properties);

        Map<String, Object> requestBody = client.buildRequestBody(List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, "system"),
                new ChatMessage(ChatMessage.Role.USER, "first question"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "first answer"),
                new ChatMessage(ChatMessage.Role.USER, "second question")
        ));

        assertThat(requestBody.get("messages")).isEqualTo(List.of(
                Map.of("role", "system", "content", "system"),
                Map.of("role", "user", "content", "first question"),
                Map.of("role", "assistant", "content", "first answer"),
                Map.of("role", "user", "content", "second question")
        ));
    }
}
