package com.uninook.question;

import java.time.LocalDateTime;

public record QuestionItem(
        Long id,
        String sourceType,
        Long sourceId,
        Long sourcePostId,
        String sourcePreview,
        Long askerId,
        String askerNickname,
        String askerAvatarUrl,
        String questionText,
        String status,
        long approvedAnswerCount,
        Long subscriberCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
