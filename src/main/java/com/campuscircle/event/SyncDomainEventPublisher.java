package com.campuscircle.event;

import com.campuscircle.notice.NoticeService;
import com.campuscircle.ai.PostSearchIndexService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!rocketmq")
public class SyncDomainEventPublisher implements DomainEventPublisher {

    private final NoticeService noticeService;
    private final PostSearchIndexService postSearchIndexService;

    public SyncDomainEventPublisher(NoticeService noticeService, PostSearchIndexService postSearchIndexService) {
        this.noticeService = noticeService;
        this.postSearchIndexService = postSearchIndexService;
    }

    @Override
    public void publishCommentCreated(CommentCreatedEvent event) {
        noticeService.createCommentNotice(
                event.eventId(), event.receiverId(), event.senderId(), event.postId(), event.commentId()
        );
    }

    @Override
    public void publishPostLiked(PostLikedEvent event) {
        noticeService.createLikeNotice(event.eventId(), event.receiverId(), event.senderId(), event.postId());
    }

    @Override
    public void publishCommentLiked(CommentLikedEvent event) {
        noticeService.createCommentLikeNotice(
                event.eventId(), event.receiverId(), event.senderId(), event.postId(), event.commentId()
        );
    }

    @Override
    public void publishQuestionLifecycle(QuestionLifecycleEvent event) {
        noticeService.createQuestionLifecycleNotices(event);
    }

    @Override
    public void publishPostSearchIndex(PostSearchIndexEvent event) {
        postSearchIndexService.reconcile(event);
    }
}
