package com.campuscircle.user;

import java.time.LocalDateTime;

public record MyLikeItem(
        String targetType,
        Long postId,
        Long commentId,
        String postTitle,
        String targetContent,
        LocalDateTime createdAt
) {
}
