package com.uninook.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "campuscircle.search", name = "enabled", havingValue = "true")
public class SearchDependencyHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(SearchDependencyHealthMonitor.class);
    private static final String EMBEDDING_PROBE = "uninook search health probe";

    private final ElasticsearchPostIndexClient indexClient;
    private final EmbeddingClient embeddingClient;
    private final SearchProperties properties;
    private final AtomicInteger elasticsearchFailures = new AtomicInteger();
    private final AtomicInteger embeddingFailures = new AtomicInteger();
    private volatile boolean elasticsearchAvailable = true;
    private volatile boolean embeddingAvailable = true;

    public SearchDependencyHealthMonitor(ElasticsearchPostIndexClient indexClient, EmbeddingClient embeddingClient,
                                         SearchProperties properties) {
        this.indexClient = indexClient;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    public boolean isElasticsearchAvailable() {
        return elasticsearchAvailable;
    }

    public boolean isEmbeddingAvailable() {
        return embeddingAvailable;
    }

    @Scheduled(fixedDelayString = "${campuscircle.search.health-check-interval-milliseconds:30000}")
    void checkDependencies() {
        probeElasticsearch();
        probeEmbedding();
    }

    void probeElasticsearch() {
        try {
            indexClient.ping();
            recordElasticsearchSuccess();
        } catch (ResourceAccessException | RestClientResponseException exception) {
            recordElasticsearchFailure(exception);
        }
    }

    void probeEmbedding() {
        try {
            embeddingClient.embed(EMBEDDING_PROBE);
            recordEmbeddingSuccess();
        } catch (ResourceAccessException | RestClientResponseException exception) {
            recordEmbeddingFailure(exception);
        }
    }

    void recordElasticsearchSuccess() {
        boolean recovered = !elasticsearchAvailable;
        elasticsearchFailures.set(0);
        elasticsearchAvailable = true;
        if (recovered) {
            log.info("search_health component=elasticsearch status=recovered");
        }
    }

    void recordEmbeddingSuccess() {
        boolean recovered = !embeddingAvailable;
        embeddingFailures.set(0);
        embeddingAvailable = true;
        if (recovered) {
            log.info("search_health component=embedding status=recovered");
        }
    }

    void recordElasticsearchFailure(Exception exception) {
        int failures = elasticsearchFailures.incrementAndGet();
        if (failures >= properties.getHealthFailureThreshold()) {
            elasticsearchAvailable = false;
        }
        log.warn("search_health component=elasticsearch status=failed consecutiveFailures={} available={} reason={}",
                failures, elasticsearchAvailable, exception.getMessage());
    }

    void recordEmbeddingFailure(Exception exception) {
        int failures = embeddingFailures.incrementAndGet();
        if (failures >= properties.getHealthFailureThreshold()) {
            embeddingAvailable = false;
        }
        log.warn("search_health component=embedding status=failed consecutiveFailures={} available={} reason={}",
                failures, embeddingAvailable, exception.getMessage());
    }
}
