package com.uninook.event;

public interface DomainEventPublisher {

    void publishCommentCreated(CommentCreatedEvent event);

    void publishPostLiked(PostLikedEvent event);

    void publishCommentLiked(CommentLikedEvent event);

    void publishQuestionLifecycle(QuestionLifecycleEvent event);

    void publishPostSearchIndex(PostSearchIndexEvent event);
}
