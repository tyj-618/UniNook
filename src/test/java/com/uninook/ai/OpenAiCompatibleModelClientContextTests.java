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

    @Test
    @SuppressWarnings("unchecked")
    void serializesFunctionDefinitionsAndToolObservations() {
        AiProperties properties = new AiProperties();
        properties.setModel("test-model");
        OpenAiCompatibleModelClient client = new OpenAiCompatibleModelClient(properties);
        ToolCall toolCall = new ToolCall("call-1", "search_posts", "{\"keyword\":\"library\"}");

        Map<String, Object> requestBody = client.buildToolRequestBody(List.of(
                new ChatMessage(ChatMessage.Role.SYSTEM, "system"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "", null, List.of(toolCall)),
                new ChatMessage(ChatMessage.Role.TOOL, "Found one post", "call-1")
        ), List.of(new ToolDefinition("search_posts", "Search posts", Map.of(
                "type", "object", "properties", Map.of("keyword", Map.of("type", "string"))
        ), ToolOperation.READ)));

        assertThat(requestBody).containsEntry("tool_choice", "auto");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) requestBody.get("tools");
        List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool).containsEntry("type", "function");
            assertThat((Map<String, Object>) tool.get("function"))
                    .containsEntry("name", "search_posts");
        });
        assertThat(messages.get(1)).containsKey("tool_calls");
        assertThat(messages.get(2)).containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call-1");
    }
}
