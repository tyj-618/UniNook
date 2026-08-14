package com.uninook.admin;

public record LowQualityAnswerItem(
        String requestId,
        long helpfulCount,
        long unhelpfulCount,
        double unhelpfulRate
) {
}
