package com.uninook.event;

import com.uninook.notice.NoticeService;
import com.uninook.ai.PostSearchIndexService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

public class RocketMqEventConsumer {

    @Component
    @Profile("rocketmq")
    @RocketMQMessageListener(
            topic = "${campuscircle.rocketmq.comment-topic}",
            consumerGroup = "campuscircle-comment-notice-consumer-group"
    )
    public static class CommentCreatedConsumer implements RocketMQListener<CommentCreatedEvent> {

        private final NoticeService noticeService;

        public CommentCreatedConsumer(NoticeService noticeService) {
            this.noticeService = noticeService;
        }

        @Override
        public void onMessage(CommentCreatedEvent event) {
            noticeService.createCommentNotice(
                    event.eventId(), event.receiverId(), event.senderId(), event.postId(), event.commentId()
            );
        }
    }

    @Component
    @Profile("rocketmq")
    @RocketMQMessageListener(
            topic = "${campuscircle.rocketmq.like-topic}",
            consumerGroup = "campuscircle-like-notice-consumer-group"
    )
    public static class PostLikedConsumer implements RocketMQListener<PostLikedEvent> {

        private final NoticeService noticeService;

        public PostLikedConsumer(NoticeService noticeService) {
            this.noticeService = noticeService;
        }

        @Override
        public void onMessage(PostLikedEvent event) {
            noticeService.createLikeNotice(event.eventId(), event.receiverId(), event.senderId(), event.postId());
        }
    }

    @Component
    @Profile("rocketmq")
    @RocketMQMessageListener(
            topic = "${campuscircle.rocketmq.comment-like-topic}",
            consumerGroup = "campuscircle-comment-like-notice-consumer-group"
    )
    public static class CommentLikedConsumer implements RocketMQListener<CommentLikedEvent> {

        private final NoticeService noticeService;

        public CommentLikedConsumer(NoticeService noticeService) {
            this.noticeService = noticeService;
        }

        @Override
        public void onMessage(CommentLikedEvent event) {
            noticeService.createCommentLikeNotice(
                    event.eventId(), event.receiverId(), event.senderId(), event.postId(), event.commentId()
            );
        }
    }

    @Component
    @Profile("rocketmq")
    @RocketMQMessageListener(
            topic = "${campuscircle.rocketmq.question-topic}",
            consumerGroup = "campuscircle-question-notice-consumer-group"
    )
    public static class QuestionLifecycleConsumer implements RocketMQListener<QuestionLifecycleEvent> {

        private final NoticeService noticeService;

        public QuestionLifecycleConsumer(NoticeService noticeService) {
            this.noticeService = noticeService;
        }

        @Override
        public void onMessage(QuestionLifecycleEvent event) {
            noticeService.createQuestionLifecycleNotices(event);
        }
    }

    @Component
    @Profile("rocketmq")
    @RocketMQMessageListener(
            topic = "${campuscircle.rocketmq.post-search-index-topic}",
            consumerGroup = "campuscircle-post-search-index-consumer-group"
    )
    public static class PostSearchIndexConsumer implements RocketMQListener<PostSearchIndexEvent> {

        private final PostSearchIndexService postSearchIndexService;

        public PostSearchIndexConsumer(PostSearchIndexService postSearchIndexService) {
            this.postSearchIndexService = postSearchIndexService;
        }

        @Override
        public void onMessage(PostSearchIndexEvent event) {
            postSearchIndexService.reconcile(event);
        }
    }
}
