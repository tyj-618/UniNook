package com.uninook.ai;

import java.time.Instant;
import java.util.Objects;

/**
 * A short-lived action draft. It is not a record of an executed business operation.
 */
public record PendingAction(
        String actionId,
        PendingActionType type,
        Long userId,
        String title,
        String content,
        Instant expiresAt
) {

    public PendingAction {
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public PendingActionSummary summary() {
        return new PendingActionSummary(actionId, type, title, content, expiresAt);
    }
}
