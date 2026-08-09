package com.campuscircle.ai;

public interface AiModelClient {

    AiModelResult generate(AiModelRequest request);

    AiTextResult generateText(AiModelRequest request);
}
