package com.uninook.question;

import java.time.LocalDateTime;

public record QuestionAnswerItem(
        Long id,
        Long questionId,
        Long commentId,
        Long postId,
        Long parentCommentId,
        Long answererId,
        String answererNickname,
        String answererAvatarUrl,
        String content,
        String status,
        Long reviewedBy,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
}
