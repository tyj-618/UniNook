package com.uninook.ai;

public interface AiModelClient {

    AiModelResult generate(java.util.List<ChatMessage> messages);

    default AiModelResult generate(AiModelRequest request) {
        return generate(request.messages());
    }

    void generateStream(java.util.List<ChatMessage> messages, AiStreamChunkConsumer chunkConsumer)
            throws java.io.IOException;

    default void generateStream(AiModelRequest request, AiStreamChunkConsumer chunkConsumer)
            throws java.io.IOException {
        generateStream(request.messages(), chunkConsumer);
    }

    AgentModelResponse generateWithTools(java.util.List<ChatMessage> messages,
                                         java.util.List<ToolDefinition> tools);

    AiTextResult generateText(AiModelRequest request);
}
