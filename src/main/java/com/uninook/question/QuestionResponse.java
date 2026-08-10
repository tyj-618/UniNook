package com.uninook.question;

import com.uninook.post.PostAuthorResponse;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionResponse(
        Long id,
        QuestionSourceType sourceType,
        Long sourceId,
        Long sourcePostId,
        String sourcePreview,
        PostAuthorResponse asker,
        String questionText,
        QuestionStatus status,
        long approvedAnswerCount,
        List<QuestionAnswerResponse> approvedAnswers,
        long subscriberCount,
        boolean subscribed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static QuestionResponse from(QuestionItem item, boolean subscribed, List<QuestionAnswerResponse> approvedAnswers) {
        return new QuestionResponse(
                item.id(),
                QuestionSourceType.valueOf(item.sourceType()),
                item.sourceId(),
                item.sourcePostId(),
                item.sourcePreview(),
                new PostAuthorResponse(item.askerId(), item.askerNickname(), item.askerAvatarUrl()),
                item.questionText(),
                QuestionStatus.valueOf(item.status()),
                item.approvedAnswerCount(),
                List.copyOf(approvedAnswers),
                item.subscriberCount(),
                subscribed,
                item.createdAt(),
                item.updatedAt()
        );
    }
}
