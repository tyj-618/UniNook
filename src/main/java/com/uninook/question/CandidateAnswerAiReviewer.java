package com.uninook.question;

import com.uninook.ai.AiModelClient;
import com.uninook.ai.AiModelRequest;
import com.uninook.ai.AiProperties;
import com.uninook.ai.AiRequestContext;
import com.uninook.ai.AiRequestRateLimiter;
import com.uninook.ai.AiTextResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidateAnswerAiReviewer {

    private static final Logger log = LoggerFactory.getLogger(CandidateAnswerAiReviewer.class);

    private static final int MIN_RELEVANCE_SCORE = 0;
    private static final int MAX_RELEVANCE_SCORE = 100;
    private static final int HEURISTIC_BASE_SCORE = 30;
    private static final int HEURISTIC_OVERLAP_WEIGHT = 70;
    private static final int RELEVANT_SCORE_THRESHOLD = 60;
    private static final int UNCERTAIN_SCORE_THRESHOLD = 40;
    private static final int DEFAULT_RELEVANCE_SCORE = 50;
    private static final int MAX_RATIONALE_LENGTH = 80;

    private static final String SYSTEM_PROMPT = """
            你是校园社区的问题答复辅助审核员。你只能判断候选答复是否回应了问题，不能判断事实真伪，
            不能替问题发起者采纳或拒绝答复，也不能补充候选答复中不存在的事实。
            请只输出 JSON 对象：
            {\"score\":0-100整数,\"verdict\":\"RELEVANT|UNCERTAIN|IRRELEVANT\",\"reason\":\"不超过60字的中文理由\"}
            """;

    private final AiModelClient aiModelClient;
    private final AiProperties aiProperties;
    private final AiRequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public CandidateAnswerAiReviewer(AiModelClient aiModelClient, AiProperties aiProperties,
                                     AiRequestRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.aiModelClient = aiModelClient;
        this.aiProperties = aiProperties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    public CandidateAnswerAiReviewResponse review(Long requesterId, QuestionItem question, QuestionAnswerItem answer) {
        rateLimiter.check(requesterId);
        if ("mock".equalsIgnoreCase(aiProperties.getProvider())) {
            return reviewWithLocalHeuristic(question, answer);
        }

        AiTextResult result = aiModelClient.generateText(new AiModelRequest(SYSTEM_PROMPT, buildUserPrompt(question, answer)));
        try {
            JsonNode payload = objectMapper.readTree(result.content());
            int score = clamp(payload.path("score").asInt(DEFAULT_RELEVANCE_SCORE));
            CandidateAnswerAiVerdict verdict = CandidateAnswerAiVerdict.valueOf(
                    payload.path("verdict").asText("UNCERTAIN").trim().toUpperCase(Locale.ROOT));
            String rationale = limit(payload.path("reason").asText("模型未提供具体理由。"), MAX_RATIONALE_LENGTH);
            return new CandidateAnswerAiReviewResponse(score, verdict, rationale, true, result.requestId());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // Expected degradation path: malformed model output (invalid JSON or unknown verdict).
            // Other runtime exceptions deliberately propagate so code bugs stay visible.
            log.warn("review requestId={} status=model-output-invalid providerRequestId={} reason={}",
                    AiRequestContext.requestId(), result.requestId(), exception.toString());
            CandidateAnswerAiReviewResponse fallback = reviewWithLocalHeuristic(question, answer);
            return new CandidateAnswerAiReviewResponse(
                    fallback.relevanceScore(),
                    CandidateAnswerAiVerdict.UNCERTAIN,
                    "模型返回格式异常，已退回基础相关性建议：" + fallback.rationale(),
                    false,
                    result.requestId()
            );
        }
    }

    private CandidateAnswerAiReviewResponse reviewWithLocalHeuristic(QuestionItem question, QuestionAnswerItem answer) {
        Set<String> questionBigrams = chineseBigrams(question.questionText());
        Set<String> answerBigrams = chineseBigrams(answer.content());
        int overlap = 0;
        for (String token : questionBigrams) {
            if (answerBigrams.contains(token)) overlap++;
        }
        int score = questionBigrams.isEmpty()
                ? DEFAULT_RELEVANCE_SCORE
                : clamp(HEURISTIC_BASE_SCORE + (int) Math.round(
                        (double) HEURISTIC_OVERLAP_WEIGHT * overlap / questionBigrams.size()));
        CandidateAnswerAiVerdict verdict = score >= RELEVANT_SCORE_THRESHOLD ? CandidateAnswerAiVerdict.RELEVANT
                : score >= UNCERTAIN_SCORE_THRESHOLD ? CandidateAnswerAiVerdict.UNCERTAIN
                        : CandidateAnswerAiVerdict.IRRELEVANT;
        String rationale = switch (verdict) {
            case RELEVANT -> "候选答复与问题存在明确的关键词或场景关联，请结合事实性进一步确认。";
            case UNCERTAIN -> "候选答复可能相关，但信息关联度不足，建议人工核对是否真正解决问题。";
            case IRRELEVANT -> "候选答复与问题的文本关联较弱，建议先确认其是否直接回应了提问。";
        };
        return new CandidateAnswerAiReviewResponse(score, verdict, rationale, false, UUID.randomUUID().toString());
    }

    private Set<String> chineseBigrams(String text) {
        String normalized = text == null ? "" : text.replaceAll("[^\\p{IsHan}]", "");
        Set<String> result = new HashSet<>();
        for (int index = 0; index + 1 < normalized.length(); index++) {
            String token = normalized.substring(index, index + 2);
            if (!Set.of("什么", "哪里", "哪些", "可以", "怎么", "请问", "一个", "一下").contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private String buildUserPrompt(QuestionItem question, QuestionAnswerItem answer) {
        return "问题：\n" + question.questionText()
                + "\n\n来源内容摘要：\n" + question.sourcePreview()
                + "\n\n候选答复：\n" + answer.content();
    }

    private int clamp(int value) {
        return Math.max(MIN_RELEVANCE_SCORE, Math.min(MAX_RELEVANCE_SCORE, value));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) return "模型未提供具体理由。";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
