package com.uninook.ai;

import java.io.IOException;

@FunctionalInterface
public interface AiStreamChunkConsumer {

    void accept(String chunk) throws IOException;

    /**
     * Delivers response metadata before text generation starts. Existing chunk-only
     * consumers remain compatible through this no-op default implementation.
     */
    default void acceptMetadata(AiAssistantStreamMetadata metadata) throws IOException {
        // Metadata is optional for non-HTTP consumers.
    }
}
