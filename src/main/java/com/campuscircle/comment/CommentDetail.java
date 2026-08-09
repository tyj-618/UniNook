package com.campuscircle.comment;

import java.time.LocalDateTime;

public record CommentDetail(
        Long id,
        Long postId,
        Long userId,
        Long postAuthorId,
        Long rootCommentId,
        Long parentCommentId,
        Long replyToUserId,
        String content,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
