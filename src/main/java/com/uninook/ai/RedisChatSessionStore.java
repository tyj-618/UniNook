package com.uninook.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Profile("redis")
public class RedisChatSessionStore implements ChatSessionStore {

    private static final String MESSAGE_KEY_PREFIX = "session:";
    private static final String MESSAGE_KEY_SUFFIX = ":messages";
    private static final String OWNER_KEY_SUFFIX = ":owner";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public RedisChatSessionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                                 AiProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<ChatMessage> load(Long userId, String sessionId) {
        String ownerId = stringRedisTemplate.opsForValue().get(ownerKey(sessionId));
        if (ownerId == null || !String.valueOf(userId).equals(ownerId)) {
            return List.of();
        }
        String payload = stringRedisTemplate.opsForValue().get(messageKey(sessionId));
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(payload, ChatMessage[].class));
        } catch (JsonProcessingException exception) {
            stringRedisTemplate.delete(List.of(messageKey(sessionId), ownerKey(sessionId)));
            return List.of();
        }
    }

    @Override
    public void save(Long userId, String sessionId, List<ChatMessage> messages) {
        Duration ttl = Duration.ofSeconds(properties.getChatSessionTtlSeconds());
        try {
            stringRedisTemplate.opsForValue().set(messageKey(sessionId), objectMapper.writeValueAsString(messages), ttl);
            stringRedisTemplate.opsForValue().set(ownerKey(sessionId), String.valueOf(userId), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize chat session messages", exception);
        }
    }

    private String messageKey(String sessionId) {
        return MESSAGE_KEY_PREFIX + sessionId + MESSAGE_KEY_SUFFIX;
    }

    private String ownerKey(String sessionId) {
        return MESSAGE_KEY_PREFIX + sessionId + OWNER_KEY_SUFFIX;
    }
}
