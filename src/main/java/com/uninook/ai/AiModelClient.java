package com.uninook.ai;

public interface AiModelClient {

    AiModelResult generate(AiModelRequest request);

    AiTextResult generateText(AiModelRequest request);
}
