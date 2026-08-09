package com.campuscircle.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Profile("redis")
public class RedisAiRequestRateLimitStore implements AiRequestRateLimitStore {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_WINDOW = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
              return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAiRequestRateLimitStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryAcquire(Long userId, int limit) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = "campuscircle:ai:rate-limit:" + userId + ':' + currentMinute;
        Long accepted = stringRedisTemplate.execute(INCREMENT_WITH_WINDOW, List.of(key), "65", String.valueOf(limit));
        return Long.valueOf(1L).equals(accepted);
    }
}
