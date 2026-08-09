package com.campuscircle.ai;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryAiRequestRateLimitStore implements AiRequestRateLimitStore {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(Long userId, int limit) {
        Instant now = Instant.now();
        return counters.compute(userId, (ignored, previous) -> {
            WindowCounter counter = previous == null || previous.windowStartedAt().plus(WINDOW).isBefore(now)
                    ? new WindowCounter(now, 1)
                    : new WindowCounter(previous.windowStartedAt(), previous.count() + 1);
            return counter;
        }).count() <= limit;
    }

    @Scheduled(fixedDelay = 300_000)
    void removeExpiredCounters() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartedAt().plus(WINDOW).isBefore(now));
    }

    private record WindowCounter(Instant windowStartedAt, int count) {
    }
}
