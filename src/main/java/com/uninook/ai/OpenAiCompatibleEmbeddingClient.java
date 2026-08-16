package com.uninook.ai;

import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible /embeddings client. Credentials are supplied only through environment variables.
 */
@Component
@ConditionalOnProperty(prefix = "campuscircle.search", name = "embedding-provider", havingValue = "openai-compatible")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    // Tree parsing stays on Jackson 2: RestClient's default converters use Jackson 3 in
    // Spring Boot 4 and cannot deserialize Jackson 2 JsonNode bodies.
    private static final ObjectMapper TREE_MAPPER = new ObjectMapper();

    private final SearchProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleEmbeddingClient(SearchProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(require(properties.getEmbeddingBaseUrl(), "CAMPUSCIRCLE_SEARCH_EMBEDDING_BASE_URL"))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + require(
                        properties.getEmbeddingApiKey(), "CAMPUSCIRCLE_SEARCH_EMBEDDING_API_KEY"))
                .requestFactory(requestFactory(properties.getEmbeddingTimeoutSeconds()))
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", require(properties.getEmbeddingModel(), "CAMPUSCIRCLE_SEARCH_EMBEDDING_MODEL"));
        request.put("input", text == null ? "" : text);
        request.put("encoding_format", "float");

        String responseBody = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        JsonNode response = parseTree(responseBody);
        JsonNode values = response == null ? null : response.path("data").path(0).path("embedding");
        if (values == null || !values.isArray()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding provider returned an invalid response");
        }

        List<Float> vector = new java.util.ArrayList<>(values.size());
        for (JsonNode value : values) {
            vector.add(value.floatValue());
        }
        if (vector.size() != properties.getEmbeddingDimensions()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Embedding dimension does not match CAMPUSCIRCLE_SEARCH_EMBEDDING_DIMENSIONS");
        }
        return vector;
    }

    private static JsonNode parseTree(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return TREE_MAPPER.readTree(responseBody);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Embedding provider returned an invalid response");
        }
    }

    private static String require(String value, String variableName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variableName + " must be configured when using openai-compatible embeddings");
        }
        return value.trim();
    }

    private static SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
