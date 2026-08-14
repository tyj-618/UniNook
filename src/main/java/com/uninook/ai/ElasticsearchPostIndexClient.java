package com.uninook.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin Elasticsearch adapter. MySQL remains the source of truth; this index only supplies candidate ranks.
 */
@Component
@ConditionalOnProperty(prefix = "campuscircle.search", name = "enabled", havingValue = "true")
public class ElasticsearchPostIndexClient {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchPostIndexClient.class);

    private final SearchProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private volatile boolean initialized;

    public ElasticsearchPostIndexClient(SearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties.getRequestTimeoutSeconds()))
                .build();
    }

    public void upsert(PostSearchDocument document) {
        ensureIndex();
        restClient.put()
                .uri("/{index}/_doc/{postId}", properties.getPostIndex(), document.postId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(toDocumentBody(document))
                .retrieve()
                .toBodilessEntity();
    }

    public void delete(Long postId) {
        ensureIndex();
        try {
            restClient.delete()
                    .uri("/{index}/_doc/{postId}", properties.getPostIndex(), postId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw exception;
            }
        }
    }

    public List<Long> searchByKeyword(String question, List<Long> allowedSchoolIds, int limit) {
        if (question == null || question.isBlank() || allowedSchoolIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> multiMatch = Map.of(
                "query", question,
                "fields", List.of("title^4", "categoryName^2", "searchText^2", "content")
        );
        Map<String, Object> body = Map.of(
                "size", limit,
                "_source", List.of("postId"),
                "query", Map.of("bool", Map.of(
                        "must", List.of(Map.of("multi_match", multiMatch)),
                        "filter", List.of(Map.of("terms", Map.of("schoolId", allowedSchoolIds)))
                ))
        );
        return executeSearch(body);
    }

    public List<Long> searchByVector(List<Float> vector, List<Long> allowedSchoolIds, int limit) {
        if (vector == null || vector.isEmpty() || allowedSchoolIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "embedding");
        knn.put("query_vector", vector);
        knn.put("k", limit);
        knn.put("num_candidates", Math.max(limit * 3, limit));
        knn.put("filter", Map.of("terms", Map.of("schoolId", allowedSchoolIds)));
        return executeSearch(Map.of("size", limit, "_source", List.of("postId"), "knn", knn));
    }

    public void ping() {
        restClient.get().uri("/").retrieve().toBodilessEntity();
    }

    private List<Long> executeSearch(Map<String, Object> body) {
        ensureIndex();
        JsonNode response = restClient.post()
                .uri("/{index}/_search", properties.getPostIndex())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            return List.of();
        }
        List<Long> postIds = new ArrayList<>();
        for (JsonNode hit : response.path("hits").path("hits")) {
            JsonNode postId = hit.path("_source").path("postId");
            if (postId.canConvertToLong()) {
                postIds.add(postId.longValue());
            }
        }
        return postIds;
    }

    private void ensureIndex() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try {
                restClient.get().uri("/{index}/_mapping", properties.getPostIndex()).retrieve().toBodilessEntity();
                initialized = true;
                return;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 404) {
                    throw exception;
                }
            }

            restClient.put()
                    .uri("/{index}", properties.getPostIndex())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(indexDefinition())
                    .retrieve()
                    .toBodilessEntity();
            initialized = true;
            log.info("Created Elasticsearch post index {}", properties.getPostIndex());
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("postId", Map.of("type", "long"));
        fields.put("schoolId", Map.of("type", "long"));
        fields.put("categoryId", Map.of("type", "long"));
        fields.put("title", Map.of("type", "text"));
        fields.put("content", Map.of("type", "text"));
        fields.put("categoryName", Map.of("type", "text"));
        fields.put("schoolName", Map.of("type", "keyword"));
        fields.put("campusName", Map.of("type", "keyword"));
        fields.put("city", Map.of("type", "keyword"));
        fields.put("searchText", Map.of("type", "text"));
        fields.put("updatedAt", Map.of("type", "date"));
        fields.put("embedding", Map.of(
                "type", "dense_vector",
                "dims", properties.getEmbeddingDimensions(),
                "index", true,
                "similarity", "cosine"
        ));
        return Map.of(
                "settings", Map.of("index", Map.of("number_of_shards", 1, "number_of_replicas", 0)),
                "mappings", Map.of("dynamic", "strict", "properties", fields)
        );
    }

    private Map<String, Object> toDocumentBody(PostSearchDocument document) {
        return objectMapper.convertValue(document, new TypeReference<>() {
        });
    }

    private static SimpleClientHttpRequestFactory requestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 1));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
