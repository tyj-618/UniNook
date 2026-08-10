package com.uninook.question;

public record QuestionSourceCleanupItem(
        Long questionId,
        String sourceType,
        Long sourceCommentId
) {
}
