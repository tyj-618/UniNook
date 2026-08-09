package com.campuscircle.question;

import com.campuscircle.post.PostAuthorResponse;

import java.time.LocalDateTime;

public record QuestionAnswerResponse(
        Long id,
        Long commentId,
        Long postId,
        Long parentCommentId,
        PostAuthorResponse answerer,
        String content,
        QuestionAnswerStatus status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static QuestionAnswerResponse from(QuestionAnswerItem item) {
        return new QuestionAnswerResponse(
                item.id(), item.commentId(), item.postId(), item.parentCommentId(),
                new PostAuthorResponse(item.answererId(), item.answererNickname(), item.answererAvatarUrl()),
                item.content(), QuestionAnswerStatus.valueOf(item.status()), item.createdAt(), item.reviewedAt()
        );
    }
}
