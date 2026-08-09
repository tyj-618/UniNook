package com.campuscircle.auth;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, FailureWindow> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String username) {
        FailureWindow window = failures.get(keyOf(username));
        if (window == null) {
            return;
        }
        if (window.startedAt().plus(WINDOW).isBefore(Instant.now())) {
            failures.remove(keyOf(username), window);
            return;
        }
        if (window.count() >= MAX_FAILURES) {
            throw new BusinessException(ErrorCode.CONFLICT, "登录尝试过于频繁，请 10 分钟后再试");
        }
    }

    public void recordFailure(String username) {
        Instant now = Instant.now();
        failures.compute(keyOf(username), (ignored, previous) -> previous == null || previous.startedAt().plus(WINDOW).isBefore(now)
                ? new FailureWindow(now, 1)
                : new FailureWindow(previous.startedAt(), previous.count() + 1));
    }

    public void clear(String username) {
        failures.remove(keyOf(username));
    }

    @Scheduled(fixedDelay = 600_000)
    void removeExpiredWindows() {
        Instant now = Instant.now();
        failures.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(WINDOW).isBefore(now));
    }

    private String keyOf(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
