package com.uninook.event;

import com.uninook.common.entity.OutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("rocketmq")
public class OutboxEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxMessageSender messageSender;
    private final ObjectMapper objectMapper;

    public OutboxEventDispatcher(OutboxEventMapper outboxEventMapper, OutboxMessageSender messageSender,
                                ObjectMapper objectMapper) {
        this.outboxEventMapper = outboxEventMapper;
        this.messageSender = messageSender;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${campuscircle.rocketmq.outbox-dispatch-interval-millis:1000}")
    public void dispatchPendingEvents() {
        for (OutboxEventEntity event : outboxEventMapper.findPending(BATCH_SIZE)) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEventEntity event) {
        try {
            OutboxEventType eventType = OutboxEventType.valueOf(event.getEventType());
            messageSender.send(eventType, deserialize(eventType, event.getPayload()));
            outboxEventMapper.markPublished(event.getId());
        } catch (RuntimeException | JsonProcessingException exception) {
            int retryDelaySeconds = Math.min(300, 1 << Math.min(event.getRetryCount(), 8));
            outboxEventMapper.scheduleRetry(event.getId(), retryDelaySeconds);
            log.warn("Failed to dispatch outbox event {}", event.getId(), exception);
        }
    }

    private Object deserialize(OutboxEventType eventType, String payload) throws JsonProcessingException {
        return switch (eventType) {
            case COMMENT_CREATED -> objectMapper.readValue(payload, CommentCreatedEvent.class);
            case POST_LIKED -> objectMapper.readValue(payload, PostLikedEvent.class);
            case COMMENT_LIKED -> objectMapper.readValue(payload, CommentLikedEvent.class);
            case QUESTION_LIFECYCLE -> objectMapper.readValue(payload, QuestionLifecycleEvent.class);
            case POST_SEARCH_INDEX -> objectMapper.readValue(payload, PostSearchIndexEvent.class);
        };
    }
}
