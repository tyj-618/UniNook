package com.campuscircle.question;

/**
 * A non-persistent recommendation. The asker still makes every review decision.
 */
public record CandidateAnswerAiReviewResponse(
        int relevanceScore,
        CandidateAnswerAiVerdict verdict,
        String rationale,
        boolean modelAssisted,
        String requestId
) {
}
