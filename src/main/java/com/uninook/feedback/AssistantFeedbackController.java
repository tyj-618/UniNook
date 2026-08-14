package com.uninook.feedback;

import com.uninook.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant/feedback")
public class AssistantFeedbackController {

    private final FeedbackService feedbackService;

    public AssistantFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<Boolean> submit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AssistantFeedbackRequest request) {
        feedbackService.submit(authorization, request);
        return ApiResponse.success(true);
    }
}
