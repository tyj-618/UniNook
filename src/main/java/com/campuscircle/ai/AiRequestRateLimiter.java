package com.campuscircle.ai;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AiRequestRateLimiter {

    private final AiProperties properties;
    private final AiRequestRateLimitStore rateLimitStore;

    public AiRequestRateLimiter(AiProperties properties, AiRequestRateLimitStore rateLimitStore) {
        this.properties = properties;
        this.rateLimitStore = rateLimitStore;
    }

    public void check(Long userId) {
        if (!rateLimitStore.tryAcquire(userId, properties.getMaxRequestsPerMinute())) {
            throw new BusinessException(ErrorCode.CONFLICT, "智能问答请求过于频繁，请稍后再试");
        }
    }
}
