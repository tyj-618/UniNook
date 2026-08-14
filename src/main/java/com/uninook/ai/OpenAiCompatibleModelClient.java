package com.uninook.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "campuscircle.ai", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleModelClient implements AiModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelClient.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatibleModelClient(AiProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        String baseUrl = properties.getBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(isBlank(baseUrl) ? "http://localhost" : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiModelResult generate(List<ChatMessage> messages) {
        ensureConfigured();
        long startedAt = System.currentTimeMillis();
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildRequestBody(messages))
                        .retrieve()
                        .body(String.class);

                AiModelResult result = parseResponse(parseProviderResponse(responseBody));
                log.info("AI call succeeded: requestId={}, model={}, elapsedMs={}, inputTokens={}, outputTokens={}",
                        result.requestId(), properties.getModel(), System.currentTimeMillis() - startedAt,
                        result.inputTokens(), result.outputTokens());
                return result;
            } catch (RestClientResponseException exception) {
                if (isRetryable(exception) && attempt < properties.getMaxRetries()) {
                    backoff(attempt);
                    continue;
                }
                log.warn("AI call failed: model={}, status={}, elapsedMs={}", properties.getModel(),
                        exception.getStatusCode().value(), System.currentTimeMillis() - startedAt);
                throw unavailable();
            } catch (ResourceAccessException exception) {
                if (attempt < properties.getMaxRetries()) {
                    backoff(attempt);
                    continue;
                }
                log.warn("AI call timed out or could not connect: model={}, elapsedMs={}", properties.getModel(),
                        System.currentTimeMillis() - startedAt);
                throw unavailable();
            }
        }
        throw unavailable();
    }

    @Override
    public AiTextResult generateText(AiModelRequest request) {
        ensureConfigured();
        long startedAt = System.currentTimeMillis();
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try {
                String responseBody = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildRequestBody(request))
                        .retrieve()
                        .body(String.class);
                ProviderResponse response = parseProviderResponse(responseBody);
                if (response == null || response.choices() == null || response.choices().isEmpty()
                        || response.choices().get(0).message() == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务返回格式异常");
                }
                String content = stripMarkdownFence(response.choices().get(0).message().content());
                String requestId = response.id() == null || response.id().isBlank() ? UUID.randomUUID().toString() : response.id();
                Integer inputTokens = response.usage() == null ? null : response.usage().inputTokens();
                Integer outputTokens = response.usage() == null ? null : response.usage().outputTokens();
                log.info("AI text call succeeded: requestId={}, model={}, elapsedMs={}, inputTokens={}, outputTokens={}",
                        requestId, properties.getModel(), System.currentTimeMillis() - startedAt, inputTokens, outputTokens);
                return new AiTextResult(content, requestId, inputTokens, outputTokens);
            } catch (RestClientResponseException exception) {
                if (isRetryable(exception) && attempt < properties.getMaxRetries()) {
                    backoff(attempt);
                    continue;
                }
                log.warn("AI text call failed: model={}, status={}, elapsedMs={}", properties.getModel(),
                        exception.getStatusCode().value(), System.currentTimeMillis() - startedAt);
                throw unavailable();
            } catch (ResourceAccessException exception) {
                if (attempt < properties.getMaxRetries()) {
                    backoff(attempt);
                    continue;
                }
                log.warn("AI text call timed out or could not connect: model={}, elapsedMs={}", properties.getModel(),
                        System.currentTimeMillis() - startedAt);
                throw unavailable();
            }
        }
        throw unavailable();
    }

    @Override
    public void generateStream(List<ChatMessage> messages, AiStreamChunkConsumer chunkConsumer) throws IOException {
        ensureConfigured();
        HttpURLConnection connection = null;
        try {
            URI endpoint = URI.create(stripTrailingSlash(properties.getBaseUrl()) + "/chat/completions");
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(Math.toIntExact(Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis()));
            connection.setReadTimeout(Math.toIntExact(Duration.ofSeconds(properties.getStreamReadTimeoutSeconds()).toMillis()));
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
            byte[] requestBody = objectMapper.writeValueAsBytes(buildStreamRequestBody(messages));
            try (var output = connection.getOutputStream()) {
                output.write(requestBody);
            }

            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new StreamHttpStatusException(status);
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(data)) {
                        return;
                    }
                    emitDeltaContent(data, chunkConsumer);
                }
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务流式返回格式异常");
        } catch (StreamHttpStatusException exception) {
            log.warn("AI stream request failed: model={}, status={}", properties.getModel(), exception.status());
            throw unavailable();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    Map<String, Object> buildRequestBody(AiModelRequest request) {
        return buildRequestBody(request.messages());
    }

    Map<String, Object> buildRequestBody(List<ChatMessage> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.providerRole(), "content", message.content()))
                .toList());
        body.put("temperature", 0.2);
        if (properties.isStructuredOutput()) {
            body.put("response_format", Map.of("type", "json_object"));
        } else {
            body.put("max_tokens", properties.getMaxOutputTokens());
        }
        if (properties.getEnableThinking() != null) {
            body.put("enable_thinking", properties.getEnableThinking());
        }
        return body;
    }

    Map<String, Object> buildStreamRequestBody(List<ChatMessage> messages) {
        Map<String, Object> body = new LinkedHashMap<>(buildRequestBody(messages));
        body.remove("response_format");
        body.put("max_tokens", properties.getMaxOutputTokens());
        body.put("stream", true);
        return body;
    }

    private void emitDeltaContent(String data, AiStreamChunkConsumer chunkConsumer) throws IOException {
        var root = objectMapper.readTree(data);
        var choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }
        var content = choices.get(0).path("delta").path("content");
        if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
            chunkConsumer.accept(content.asText());
        }
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private ProviderResponse parseProviderResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, ProviderResponse.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务返回格式异常");
        }
    }

    private AiModelResult parseResponse(ProviderResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务返回格式异常");
        }
        String content = stripMarkdownFence(response.choices().get(0).message().content());
        try {
            ModelAnswerPayload payload = objectMapper.readValue(content, ModelAnswerPayload.class);
            if (payload.answer() == null || payload.answer().isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务未返回有效答案");
            }
            return new AiModelResult(
                    payload.answer().trim(),
                    payload.citedPostIds() == null ? List.of() : payload.citedPostIds(),
                    Boolean.TRUE.equals(payload.insufficientEvidence()),
                    response.id() == null || response.id().isBlank() ? UUID.randomUUID().toString() : response.id(),
                    response.usage() == null ? null : response.usage().inputTokens(),
                    response.usage() == null ? null : response.usage().outputTokens()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务返回格式异常");
        }
    }

    private void ensureConfigured() {
        if (isBlank(properties.getBaseUrl()) || isBlank(properties.getApiKey()) || isBlank(properties.getModel())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型服务未完成配置");
        }
    }

    private boolean isRetryable(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * (attempt + 1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INTERNAL_ERROR, "智能问答服务暂不可用，请稍后再试");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stripMarkdownFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private record ProviderMessage(String role, String content) {
    }

    private record ProviderResponse(String id, List<ProviderChoice> choices, ProviderUsage usage) {
    }

    private record ProviderChoice(ProviderMessage message) {
    }

    private record ProviderUsage(
            @JsonProperty("prompt_tokens") Integer inputTokens,
            @JsonProperty("completion_tokens") Integer outputTokens
    ) {
    }

    private record ModelAnswerPayload(String answer, List<Long> citedPostIds, Boolean insufficientEvidence) {
    }

    private static final class StreamHttpStatusException extends IOException {

        private final int status;

        private StreamHttpStatusException(int status) {
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
