package com.campuscircle.event;

import java.util.UUID;

public record CommentLikedEvent(
        String eventId,
        Long receiverId,
        Long senderId,
        Long postId,
        Long commentId
) {
    public static CommentLikedEvent create(Long receiverId, Long senderId, Long postId, Long commentId) {
        return new CommentLikedEvent(UUID.randomUUID().toString(), receiverId, senderId, postId, commentId);
    }
}
