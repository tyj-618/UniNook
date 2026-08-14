package com.uninook.feedback;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class FeedbackService {

    private final CurrentUserService currentUserService;
    private final FeedbackMapper feedbackMapper;

    public FeedbackService(CurrentUserService currentUserService, FeedbackMapper feedbackMapper) {
        this.currentUserService = currentUserService;
        this.feedbackMapper = feedbackMapper;
    }

    @Transactional
    public void submit(String authorization, AssistantFeedbackRequest request) {
        Long userId = currentUserService.requireUserId(authorization);
        FeedbackRating rating = parseRating(request.rating());
        String comment = trimToNull(request.comment());
        String question = trimToNull(request.question());
        if (feedbackMapper.countByUserAndRequest(userId, request.requestId()) > 0) {
            feedbackMapper.update(userId, request.requestId(), rating.name(), comment, question);
            return;
        }
        feedbackMapper.insert(userId, request.requestId(), rating.name(), comment, question);
    }

    private FeedbackRating parseRating(String value) {
        try {
            return FeedbackRating.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "反馈类型仅支持 HELPFUL 或 UNHELPFUL");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
