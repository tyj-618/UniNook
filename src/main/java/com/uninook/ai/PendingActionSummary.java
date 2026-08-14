package com.uninook.ai;

import java.time.Instant;

/**
 * Client-safe metadata for a pending action. The owner identity remains server-side.
 */
public record PendingActionSummary(
        String actionId,
        PendingActionType type,
        String title,
        String content,
        Instant expiresAt
) {
}
