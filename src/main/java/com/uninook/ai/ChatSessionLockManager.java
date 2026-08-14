package com.uninook.ai;

/**
 * Serializes updates to one user's hot chat session while an answer is being generated.
 */
public interface ChatSessionLockManager {

    SessionLock acquire(Long userId, String sessionId);

    interface SessionLock extends AutoCloseable {

        @Override
        void close();
    }
}
