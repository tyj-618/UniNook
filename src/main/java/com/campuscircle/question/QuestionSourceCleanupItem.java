package com.campuscircle.question;

public record QuestionSourceCleanupItem(
        Long questionId,
        String sourceType,
        Long sourceCommentId
) {
}
