package com.uninook.admin;

public record FeedbackRatingSummary(
        String requestId,
        long helpfulCount,
        long unhelpfulCount
) {
    public double unhelpfulRate() {
        long total = helpfulCount + unhelpfulCount;
        return total == 0 ? 0 : (double) unhelpfulCount / total;
    }
}
