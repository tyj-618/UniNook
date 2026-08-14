package com.uninook.ai;

import java.util.List;

/**
 * Keeps the context budget bounded. Semantic-summary compression can replace this later.
 */
public interface ChatContextCompressor {

    List<ChatMessage> compress(List<ChatMessage> messages);
}
