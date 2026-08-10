package com.uninook.event;

import java.util.UUID;

/**
 * Requests reconciliation of one post document with the latest database state.
 */
public record PostSearchIndexEvent(String eventId, Long postId) {

    public static PostSearchIndexEvent forPost(Long postId) {
        return new PostSearchIndexEvent(UUID.randomUUID().toString(), postId);
    }
}
