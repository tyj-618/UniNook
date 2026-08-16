package com.uninook.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uninook.ai.AiModelClient;
import com.uninook.ai.AiProperties;
import com.uninook.ai.AiRequestRateLimiter;
import com.uninook.ai.AiTextResult;
import com.uninook.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CandidateAnswerAiReviewerTests {

    private final AiModelClient modelClient = mock(AiModelClient.class);

    @Test
    void parsesValidModelJsonIntoStructuredReview() {
        when(modelClient.generateText(any())).thenReturn(new AiTextResult(
                "{\"score\":88,\"verdict\":\"RELEVANT\",\"reason\":\"答复直接回应了问题。\"}", "req-1", 10, 20));

        CandidateAnswerAiReviewResponse response = reviewer("real-provider")
                .review(7L, question("图书馆几点开门"), answer("图书馆晚上十点关门。"));

        assertThat(response.relevanceScore()).isEqualTo(88);
        assertThat(response.verdict()).isEqualTo(CandidateAnswerAiVerdict.RELEVANT);
        assertThat(response.rationale()).isEqualTo("答复直接回应了问题。");
        assertThat(response.modelAssisted()).isTrue();
        assertThat(response.requestId()).isEqualTo("req-1");
    }

    @Test
    void clampsOutOfRangeScoresIntoTheValidRange() {
        when(modelClient.generateText(any())).thenReturn(new AiTextResult(
                "{\"score\":500,\"verdict\":\"relevant\",\"reason\":\"ok\"}", "req-2", 0, 0));

        CandidateAnswerAiReviewResponse response = reviewer("real-provider")
                .review(7L, question("图书馆几点开门"), answer("图书馆晚上十点关门。"));

        assertThat(response.relevanceScore()).isEqualTo(100);
        assertThat(response.verdict()).isEqualTo(CandidateAnswerAiVerdict.RELEVANT);
    }

    @Test
    void fallsBackToHeuristicWhenModelReturnsInvalidJson() {
        when(modelClient.generateText(any())).thenReturn(new AiTextResult("not a json payload", "req-3", 0, 0));

        CandidateAnswerAiReviewResponse response = reviewer("real-provider")
                .review(7L, question("图书馆几点开门"), answer("图书馆晚上十点关门。"));

        assertThat(response.verdict()).isEqualTo(CandidateAnswerAiVerdict.UNCERTAIN);
        assertThat(response.modelAssisted()).isFalse();
        assertThat(response.rationale()).startsWith("模型返回格式异常");
        assertThat(response.requestId()).isEqualTo("req-3");
    }

    @Test
    void fallsBackToHeuristicWhenModelReturnsAnUnknownVerdict() {
        when(modelClient.generateText(any())).thenReturn(new AiTextResult(
                "{\"score\":70,\"verdict\":\"MAYBE\",\"reason\":\"unknown verdict\"}", "req-4", 0, 0));

        CandidateAnswerAiReviewResponse response = reviewer("real-provider")
                .review(7L, question("图书馆几点开门"), answer("图书馆晚上十点关门。"));

        assertThat(response.verdict()).isEqualTo(CandidateAnswerAiVerdict.UNCERTAIN);
        assertThat(response.modelAssisted()).isFalse();
    }

    @Test
    void usesTheLocalHeuristicWithoutCallingTheModelInMockMode() {
        CandidateAnswerAiReviewResponse overlapping = reviewer("mock")
                .review(7L, question("图书馆几点开门"), answer("图书馆晚上十点开门，周末照常开放。"));
        CandidateAnswerAiReviewResponse unrelated = reviewer("mock")
                .review(7L, question("食堂夜宵供应到几点"), answer("篮球场周末开放。"));

        assertThat(overlapping.verdict()).isEqualTo(CandidateAnswerAiVerdict.RELEVANT);
        assertThat(overlapping.modelAssisted()).isFalse();
        assertThat(unrelated.verdict()).isEqualTo(CandidateAnswerAiVerdict.IRRELEVANT);
        verifyNoInteractions(modelClient);
    }

    @Test
    void rateLimitsBeforeCallingTheModel() {
        AiProperties properties = new AiProperties();
        properties.setProvider("real-provider");
        CandidateAnswerAiReviewer reviewer = new CandidateAnswerAiReviewer(modelClient, properties,
                new AiRequestRateLimiter(properties, (userId, limit) -> false), new ObjectMapper());

        assertThatThrownBy(() -> reviewer.review(7L, question("图书馆几点开门"), answer("十点关门。")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(modelClient);
    }

    private CandidateAnswerAiReviewer reviewer(String provider) {
        AiProperties properties = new AiProperties();
        properties.setProvider(provider);
        return new CandidateAnswerAiReviewer(modelClient, properties,
                new AiRequestRateLimiter(properties, (userId, limit) -> true), new ObjectMapper());
    }

    private QuestionItem question(String text) {
        return new QuestionItem(1L, "POST", 11L, 11L, "来源摘要", 7L, "提问人", null,
                text, "OPEN", 0, 0L, LocalDateTime.now(), LocalDateTime.now());
    }

    private QuestionAnswerItem answer(String content) {
        return new QuestionAnswerItem(2L, 1L, 21L, 11L, null, 8L, "回答人", null,
                content, "PENDING", null, LocalDateTime.now(), null);
    }
}
