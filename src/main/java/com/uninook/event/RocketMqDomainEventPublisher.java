package com.uninook.event;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rocketmq")
public class RocketMqDomainEventPublisher implements OutboxMessageSender {

    private final RocketMQTemplate rocketMQTemplate;
    private final String commentTopic;
    private final String likeTopic;
    private final String commentLikeTopic;
    private final String questionTopic;
    private final String postSearchIndexTopic;

    public RocketMqDomainEventPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${campuscircle.rocketmq.comment-topic}") String commentTopic,
            @Value("${campuscircle.rocketmq.like-topic}") String likeTopic,
            @Value("${campuscircle.rocketmq.comment-like-topic}") String commentLikeTopic,
            @Value("${campuscircle.rocketmq.question-topic}") String questionTopic,
            @Value("${campuscircle.rocketmq.post-search-index-topic}") String postSearchIndexTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.commentTopic = commentTopic;
        this.likeTopic = likeTopic;
        this.commentLikeTopic = commentLikeTopic;
        this.questionTopic = questionTopic;
        this.postSearchIndexTopic = postSearchIndexTopic;
    }

    @Override
    public void send(OutboxEventType eventType, Object event) {
        String topic = switch (eventType) {
            case COMMENT_CREATED -> commentTopic;
            case POST_LIKED -> likeTopic;
            case COMMENT_LIKED -> commentLikeTopic;
            case QUESTION_LIFECYCLE -> questionTopic;
            case POST_SEARCH_INDEX -> postSearchIndexTopic;
        };
        rocketMQTemplate.convertAndSend(topic, event);
    }
}
