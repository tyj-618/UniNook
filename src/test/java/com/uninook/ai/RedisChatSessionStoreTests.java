package com.uninook.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisChatSessionStoreTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisChatSessionStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AiProperties properties = new AiProperties();
        properties.setChatSessionTtlSeconds(1800);
        store = new RedisChatSessionStore(redisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void savesJsonMessagesWithConfiguredTtl() {
        store.save(7L, "session-1", List.of(
                new ChatMessage(ChatMessage.Role.USER, "first question"),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "first answer")
        ));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("session:session-1:messages"), payload.capture(), eq(Duration.ofMinutes(30)));
        verify(valueOperations).set("session:session-1:owner", "7", Duration.ofMinutes(30));
        assertThat(payload.getValue()).contains("first question", "first answer", "USER", "ASSISTANT");
    }

    @Test
    void doesNotLoadAnotherUsersSession() {
        when(valueOperations.get("session:session-1:owner")).thenReturn("8");

        assertThat(store.load(7L, "session-1")).isEmpty();

        verify(valueOperations, never()).get("session:session-1:messages");
    }
}
