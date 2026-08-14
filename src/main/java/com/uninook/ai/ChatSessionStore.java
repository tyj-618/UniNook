package com.uninook.ai;

import java.util.List;

/**
 * Hot conversation storage. A future cold-store implementation can restore expired sessions.
 */
public interface ChatSessionStore {

    List<ChatMessage> load(Long userId, String sessionId);

    void save(Long userId, String sessionId, List<ChatMessage> messages);
}
