package com.uninook.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("redis")
public class RedisTokenStore implements TokenStore {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final String TOKEN_KEY_PREFIX = "campuscircle:auth:token:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "campuscircle:auth:refresh:";
    private static final DefaultRedisScript<String> CONSUME_REFRESH_TOKEN = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value;",
            String.class
    );

    private final TokenGenerator tokenGenerator;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisTokenStore(TokenGenerator tokenGenerator, StringRedisTemplate stringRedisTemplate) {
        this.tokenGenerator = tokenGenerator;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public TokenSession createSession(Long userId) {
        String token = tokenGenerator.generate();
        String refreshToken = tokenGenerator.generate();
        stringRedisTemplate.opsForValue().set(buildTokenKey(token), userId.toString(), ACCESS_TOKEN_TTL);
        stringRedisTemplate.opsForValue().set(buildRefreshTokenKey(refreshToken), userId + ":" + token, REFRESH_TOKEN_TTL);
        return new TokenSession(token, refreshToken, userId, ACCESS_TOKEN_TTL.toSeconds(), REFRESH_TOKEN_TTL.toSeconds());
    }

    @Override
    public Optional<TokenSession> refreshSession(String refreshToken) {
        String value = stringRedisTemplate.execute(CONSUME_REFRESH_TOKEN, java.util.List.of(buildRefreshTokenKey(refreshToken)));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] values = value.split(":", 2);
        if (values.length != 2) {
            return Optional.empty();
        }
        stringRedisTemplate.delete(buildTokenKey(values[1]));
        return Optional.of(createSession(Long.parseLong(values[0])));
    }

    @Override
    public Optional<Long> findUserId(String token) {
        String userId = stringRedisTemplate.opsForValue().get(buildTokenKey(token));
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(userId));
    }

    @Override
    public void remove(String token) {
        stringRedisTemplate.delete(buildTokenKey(token));
    }

    @Override
    public void removeRefreshToken(String refreshToken) {
        String value = stringRedisTemplate.execute(CONSUME_REFRESH_TOKEN, java.util.List.of(buildRefreshTokenKey(refreshToken)));
        if (value != null && value.contains(":")) {
            stringRedisTemplate.delete(buildTokenKey(value.substring(value.indexOf(':') + 1)));
        }
    }

    private String buildTokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String buildRefreshTokenKey(String token) {
        return REFRESH_TOKEN_KEY_PREFIX + token;
    }
}
