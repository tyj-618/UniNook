package com.campuscircle.notice;

public record CreateNoticeCommand(
        Long receiverId,
        Long senderId,
        Long postId,
        Long commentId,
        Long questionId,
        Integer type,
        String eventKey,
        String content
) {
}
