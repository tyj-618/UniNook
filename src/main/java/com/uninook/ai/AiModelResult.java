package com.uninook.ai;

import java.util.List;

public record AiModelResult(
        String answer,
        List<Long> citedPostIds,
        boolean insufficientEvidence,
        String requestId,
        Integer inputTokens,
        Integer outputTokens
) {
}
