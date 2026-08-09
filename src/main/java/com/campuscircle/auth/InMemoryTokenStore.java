package com.campuscircle.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryTokenStore implements TokenStore {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    private final TokenGenerator tokenGenerator;
    private final Map<String, StoredToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, StoredRefreshToken> refreshTokens = new ConcurrentHashMap<>();

    public InMemoryTokenStore(TokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public synchronized TokenSession createSession(Long userId) {
        String token = tokenGenerator.generate();
        String refreshToken = tokenGenerator.generate();
        Instant now = Instant.now();
        tokens.put(token, new StoredToken(userId, now.plus(ACCESS_TOKEN_TTL)));
        refreshTokens.put(refreshToken, new StoredRefreshToken(userId, token, now.plus(REFRESH_TOKEN_TTL)));
        return new TokenSession(token, refreshToken, userId, ACCESS_TOKEN_TTL.toSeconds(), REFRESH_TOKEN_TTL.toSeconds());
    }

    @Override
    public synchronized Optional<TokenSession> refreshSession(String refreshToken) {
        StoredRefreshToken storedRefreshToken = refreshTokens.remove(refreshToken);
        if (storedRefreshToken == null || storedRefreshToken.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        tokens.remove(storedRefreshToken.accessToken());
        return Optional.of(createSession(storedRefreshToken.userId()));
    }

    @Override
    public Optional<Long> findUserId(String token) {
        StoredToken storedToken = tokens.get(token);
        if (storedToken == null) {
            return Optional.empty();
        }

        if (storedToken.expiresAt().isBefore(Instant.now())) {
            tokens.remove(token);
            return Optional.empty();
        }

        return Optional.of(storedToken.userId());
    }

    @Override
    public void remove(String token) {
        tokens.remove(token);
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().accessToken().equals(token));
    }

    @Override
    public void removeRefreshToken(String refreshToken) {
        StoredRefreshToken refreshTokenRecord = refreshTokens.remove(refreshToken);
        if (refreshTokenRecord != null) {
            tokens.remove(refreshTokenRecord.accessToken());
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void removeExpiredTokens() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record StoredToken(Long userId, Instant expiresAt) {
    }

    private record StoredRefreshToken(Long userId, String accessToken, Instant expiresAt) {
    }
}
