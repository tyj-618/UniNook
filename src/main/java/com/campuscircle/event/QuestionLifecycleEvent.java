package com.campuscircle.event;

import java.util.List;
import java.util.UUID;

public record QuestionLifecycleEvent(
        String eventId,
        QuestionLifecycleEventType type,
        Long questionId,
        Long senderId,
        Long postId,
        Long commentId,
        String answerPreview,
        List<Long> receiverIds
) {
    public static QuestionLifecycleEvent candidateSubmitted(Long questionId, Long askerId, Long answererId,
                                                            Long postId, Long commentId, String preview) {
        return new QuestionLifecycleEvent(UUID.randomUUID().toString(), QuestionLifecycleEventType.CANDIDATE_SUBMITTED,
                questionId, answererId, postId, commentId, preview, List.of(askerId));
    }

    public static QuestionLifecycleEvent answerAccepted(Long questionId, Long askerId, Long postId, Long commentId,
                                                        String preview, List<Long> subscriberIds) {
        return new QuestionLifecycleEvent(UUID.randomUUID().toString(), QuestionLifecycleEventType.ANSWER_ACCEPTED,
                questionId, askerId, postId, commentId, preview, List.copyOf(subscriberIds));
    }

    public static QuestionLifecycleEvent completed(Long questionId, Long askerId, Long postId, Long commentId,
                                                   String preview, List<Long> subscriberIds) {
        return new QuestionLifecycleEvent(UUID.randomUUID().toString(), QuestionLifecycleEventType.COMPLETED,
                questionId, askerId, postId, commentId, preview, List.copyOf(subscriberIds));
    }

    public static QuestionLifecycleEvent reopened(Long questionId, Long askerId, Long postId, Long commentId,
                                                  List<Long> subscriberIds) {
        return new QuestionLifecycleEvent(UUID.randomUUID().toString(), QuestionLifecycleEventType.REOPENED,
                questionId, askerId, postId, commentId, null, List.copyOf(subscriberIds));
    }

    public static QuestionLifecycleEvent deleted(Long questionId, Long askerId, Long postId, Long commentId,
                                                 List<Long> subscriberIds) {
        return new QuestionLifecycleEvent(UUID.randomUUID().toString(), QuestionLifecycleEventType.DELETED,
                questionId, askerId, postId, commentId, null, List.copyOf(subscriberIds));
    }
}
