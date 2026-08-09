package com.campuscircle.question;

import com.campuscircle.common.ApiResponse;
import com.campuscircle.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionAnswerService questionAnswerService;

    public QuestionController(QuestionService questionService, QuestionAnswerService questionAnswerService) {
        this.questionService = questionService;
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping("/questions")
    public ApiResponse<QuestionResponse> createQuestion(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateQuestionRequest request) {
        return ApiResponse.success(questionService.createQuestion(authorization, request));
    }

    @GetMapping("/questions/by-source")
    public ApiResponse<QuestionResponse> findQuestionBySource(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam QuestionSourceType sourceType,
            @RequestParam Long sourceId) {
        return ApiResponse.success(questionService.findBySource(authorization, sourceType, sourceId));
    }

    @GetMapping("/questions/by-sources")
    public ApiResponse<java.util.Map<Long, QuestionSourceSummary>> findQuestionsBySources(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam QuestionSourceType sourceType,
            @RequestParam @Size(max = 50) java.util.List<Long> sourceIds) {
        return ApiResponse.success(questionService.findBySources(authorization, sourceType, sourceIds));
    }

    @GetMapping("/questions/{questionId}")
    public ApiResponse<QuestionResponse> findQuestion(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionService.findById(authorization, questionId));
    }

    @PostMapping("/questions/{questionId}/subscriptions")
    public ApiResponse<QuestionSubscriptionResponse> subscribe(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionService.subscribe(authorization, questionId));
    }

    @DeleteMapping("/questions/{questionId}/subscriptions")
    public ApiResponse<QuestionSubscriptionResponse> unsubscribe(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionService.unsubscribe(authorization, questionId));
    }

    @GetMapping("/questions/{questionId}/answers")
    public ApiResponse<java.util.List<QuestionAnswerResponse>> listAnswers(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.listAnswers(authorization, questionId));
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/ai-review")
    public ApiResponse<CandidateAnswerAiReviewResponse> reviewAnswerWithAi(
            @PathVariable Long questionId, @PathVariable Long answerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.reviewWithAi(authorization, questionId, answerId));
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/accept")
    public ApiResponse<QuestionResponse> acceptAnswer(
            @PathVariable Long questionId, @PathVariable Long answerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.accept(authorization, questionId, answerId));
    }

    @PostMapping("/questions/{questionId}/complete")
    public ApiResponse<QuestionResponse> completeQuestion(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.complete(authorization, questionId));
    }

    @PostMapping("/questions/{questionId}/reopen")
    public ApiResponse<QuestionResponse> reopenQuestion(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.reopen(authorization, questionId));
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/reject")
    public ApiResponse<QuestionAnswerResponse> rejectAnswer(
            @PathVariable Long questionId, @PathVariable Long answerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(questionAnswerService.reject(authorization, questionId, answerId));
    }

    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<Void> deleteQuestion(
            @PathVariable Long questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        questionService.deleteQuestion(authorization, questionId);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/questions")
    public ApiResponse<PageResponse<QuestionResponse>> listMyQuestions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam MyQuestionRole role,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.success(questionService.listMyQuestions(authorization, role, page, size));
    }
}
