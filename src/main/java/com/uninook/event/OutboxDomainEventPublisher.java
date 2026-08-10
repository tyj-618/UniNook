package com.uninook.event;

import com.uninook.common.entity.OutboxEventEntity;
import com.uninook.exception.BusinessException;
import com.uninook.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("rocketmq")
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(OutboxEventMapper outboxEventMapper, ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishCommentCreated(CommentCreatedEvent event) {
        save(event.eventId(), OutboxEventType.COMMENT_CREATED, event);
    }

    @Override
    public void publishPostLiked(PostLikedEvent event) {
        save(event.eventId(), OutboxEventType.POST_LIKED, event);
    }

    @Override
    public void publishCommentLiked(CommentLikedEvent event) {
        save(event.eventId(), OutboxEventType.COMMENT_LIKED, event);
    }

    @Override
    public void publishQuestionLifecycle(QuestionLifecycleEvent event) {
        save(event.eventId(), OutboxEventType.QUESTION_LIFECYCLE, event);
    }

    @Override
    public void publishPostSearchIndex(PostSearchIndexEvent event) {
        save(event.eventId(), OutboxEventType.POST_SEARCH_INDEX, event);
    }

    private void save(String eventId, OutboxEventType eventType, Object event) {
        try {
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(eventId);
            entity.setEventType(eventType.name());
            entity.setPayload(objectMapper.writeValueAsString(event));
            entity.setStatus(0);
            entity.setRetryCount(0);
            entity.setNextAttemptAt(LocalDateTime.now());
            outboxEventMapper.insert(entity);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to serialize domain event");
        }
    }
}
