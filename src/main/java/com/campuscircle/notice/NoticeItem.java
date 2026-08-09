package com.campuscircle.notice;

import java.time.LocalDateTime;

public record NoticeItem(
        Long id,
        Long receiverId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        Long postId,
        Long commentId,
        Long questionId,
        Integer type,
        String content,
        Integer readStatus,
        boolean targetDeleted,
        String targetDeletedMessage,
        LocalDateTime createdAt
) {
}
