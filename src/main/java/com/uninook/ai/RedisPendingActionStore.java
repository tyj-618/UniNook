package com.uninook.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("redis")
public class RedisPendingActionStore implements PendingActionStore {

    private static final String KEY_PREFIX = "ai:pending-action:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public RedisPendingActionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                                   AiProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void save(PendingAction action) {
        try {
            stringRedisTemplate.opsForValue().set(key(action.userId(), action.actionId()),
                    objectMapper.writeValueAsString(action),
                    Duration.ofSeconds(properties.getPendingActionTtlSeconds()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize pending action", exception);
        }
    }

    @Override
    public Optional<PendingAction> load(Long userId, String actionId) {
        return deserialize(userId, actionId, stringRedisTemplate.opsForValue().get(key(userId, actionId)));
    }

    @Override
    public Optional<PendingAction> take(Long userId, String actionId) {
        return deserialize(userId, actionId, stringRedisTemplate.opsForValue().getAndDelete(key(userId, actionId)));
    }

    @Override
    public void delete(Long userId, String actionId) {
        stringRedisTemplate.delete(key(userId, actionId));
    }

    private Optional<PendingAction> deserialize(Long userId, String actionId, String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            PendingAction action = objectMapper.readValue(payload, PendingAction.class);
            if (!userId.equals(action.userId())) {
                stringRedisTemplate.delete(key(userId, actionId));
                return Optional.empty();
            }
            return Optional.of(action);
        } catch (JsonProcessingException exception) {
            stringRedisTemplate.delete(key(userId, actionId));
            return Optional.empty();
        }
    }

    private String key(Long userId, String actionId) {
        return KEY_PREFIX + userId + ':' + actionId;
    }
}
