package com.uninook.ai;

public interface AiModelClient {

    AiModelResult generate(java.util.List<ChatMessage> messages);

    default AiModelResult generate(AiModelRequest request) {
        return generate(request.messages());
    }

    AiTextResult generateText(AiModelRequest request);
}
