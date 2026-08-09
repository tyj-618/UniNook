package com.campuscircle.ai;

public interface AiRequestRateLimitStore {

    boolean tryAcquire(Long userId, int limit);
}
