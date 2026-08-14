package com.uninook.ai;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@Profile("redis")
public class RedisChatSessionLockManager implements ChatSessionLockManager {

    private static final SessionLock NO_OP_LOCK = () -> { };
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties properties;

    public RedisChatSessionLockManager(StringRedisTemplate stringRedisTemplate, AiProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public SessionLock acquire(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return NO_OP_LOCK;
        }
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(userId, sessionId), token,
                Duration.ofSeconds(properties.getChatSessionLockTtlSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该会话正在生成回复，请等待当前回复完成后再提问");
        }
        return () -> stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(userId, sessionId)), token);
    }

    private String lockKey(Long userId, String sessionId) {
        return "session:" + sessionId + ":owner:" + userId + ":lock";
    }
}
