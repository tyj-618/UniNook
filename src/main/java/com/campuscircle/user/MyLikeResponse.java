package com.campuscircle.user;

import java.time.LocalDateTime;

public record MyLikeResponse(
        String targetType,
        Long postId,
        Long commentId,
        String postTitle,
        String targetContent,
        LocalDateTime createdAt
) {

    public static MyLikeResponse from(MyLikeItem item) {
        return new MyLikeResponse(
                item.targetType(),
                item.postId(),
                item.commentId(),
                item.postTitle(),
                item.targetContent(),
                item.createdAt()
        );
    }
}
