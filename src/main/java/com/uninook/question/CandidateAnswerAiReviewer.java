package com.uninook.question;

import com.uninook.ai.AiModelClient;
import com.uninook.ai.AiModelRequest;
import com.uninook.ai.AiProperties;
import com.uninook.ai.AiRequestRateLimiter;
import com.uninook.ai.AiTextResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidateAnswerAiReviewer {

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
            int score = clamp(payload.path("score").asInt(50));
            CandidateAnswerAiVerdict verdict = CandidateAnswerAiVerdict.valueOf(
                    payload.path("verdict").asText("UNCERTAIN").trim().toUpperCase(Locale.ROOT));
            String rationale = limit(payload.path("reason").asText("模型未提供具体理由。"), 80);
            return new CandidateAnswerAiReviewResponse(score, verdict, rationale, true, result.requestId());
        } catch (Exception ignored) {
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
        int score = questionBigrams.isEmpty() ? 50 : clamp(30 + (int) Math.round(70.0 * overlap / questionBigrams.size()));
        CandidateAnswerAiVerdict verdict = score >= 60 ? CandidateAnswerAiVerdict.RELEVANT
                : score >= 40 ? CandidateAnswerAiVerdict.UNCERTAIN : CandidateAnswerAiVerdict.IRRELEVANT;
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
        return Math.max(0, Math.min(100, value));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) return "模型未提供具体理由。";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
