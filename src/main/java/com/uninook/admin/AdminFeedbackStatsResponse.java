package com.uninook.admin;

import java.util.List;

public record AdminFeedbackStatsResponse(
        List<LowQualityAnswerItem> lowQualityAnswers,
        List<FrequentQuestionItem> frequentQuestions
) {
}
