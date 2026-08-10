package com.uninook.ai;

public interface AiRequestRateLimitStore {

    boolean tryAcquire(Long userId, int limit);
}
