package com.campuscircle.question;

import com.campuscircle.post.PostAuthorResponse;

/**
 * A compact question view for rendering multiple source items on one page.
 */
public record QuestionSourceSummary(
        Long id,
        QuestionSourceType sourceType,
        Long sourceId,
        Long sourcePostId,
        PostAuthorResponse asker,
        String questionText,
        QuestionStatus status,
        long approvedAnswerCount,
        long subscriberCount,
        boolean subscribed
) {
    public static QuestionSourceSummary from(QuestionItem item, boolean subscribed) {
        return new QuestionSourceSummary(
                item.id(),
                QuestionSourceType.valueOf(item.sourceType()),
                item.sourceId(),
                item.sourcePostId(),
                new PostAuthorResponse(item.askerId(), item.askerNickname(), item.askerAvatarUrl()),
                item.questionText(),
                QuestionStatus.valueOf(item.status()),
                item.approvedAnswerCount(),
                item.subscriberCount(),
                subscribed
        );
    }
}
