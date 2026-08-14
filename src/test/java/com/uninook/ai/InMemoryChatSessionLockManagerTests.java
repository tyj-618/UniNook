package com.uninook.ai;

import com.uninook.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryChatSessionLockManagerTests {

    @Test
    void rejectsConcurrentGenerationForTheSameUserSessionAndReleasesAfterCompletion() {
        InMemoryChatSessionLockManager lockManager = new InMemoryChatSessionLockManager();

        try (ChatSessionLockManager.SessionLock ignored = lockManager.acquire(7L, "session-1")) {
            assertThatThrownBy(() -> CompletableFuture.runAsync(
                    () -> lockManager.acquire(7L, "session-1")).join())
                    .hasCauseInstanceOf(BusinessException.class);
        }

        assertThatCode(() -> {
            try (ChatSessionLockManager.SessionLock ignored = lockManager.acquire(7L, "session-1")) {
                // Lock is available again after the first response completes.
            }
        }).doesNotThrowAnyException();
    }
}
