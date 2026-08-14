package com.uninook.ai;

import com.uninook.post.PostListItem;
import com.uninook.post.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves keyword and semantic candidates independently and fuses available ranks with RRF.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "campuscircle.search", name = "enabled", havingValue = "true")
public class HybridPostRetriever implements PostRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridPostRetriever.class);

    private final PostRetrievalService sqlRetriever;
    private final ElasticsearchPostIndexClient indexClient;
    private final EmbeddingClient embeddingClient;
    private final PostMapper postMapper;
    private final SearchProperties properties;
    private final SearchDependencyHealthMonitor healthMonitor;
    private final AiOperationalMetrics metrics;

    public HybridPostRetriever(PostRetrievalService sqlRetriever, ElasticsearchPostIndexClient indexClient,
                               EmbeddingClient embeddingClient, PostMapper postMapper, SearchProperties properties,
                               SearchDependencyHealthMonitor healthMonitor) {
        this(sqlRetriever, indexClient, embeddingClient, postMapper, properties, healthMonitor, AiOperationalMetrics.noOp());
    }

    @Autowired
    public HybridPostRetriever(PostRetrievalService sqlRetriever, ElasticsearchPostIndexClient indexClient,
                               EmbeddingClient embeddingClient, PostMapper postMapper, SearchProperties properties,
                               SearchDependencyHealthMonitor healthMonitor, AiOperationalMetrics metrics) {
        this.sqlRetriever = sqlRetriever;
        this.indexClient = indexClient;
        this.embeddingClient = embeddingClient;
        this.postMapper = postMapper;
        this.properties = properties;
        this.healthMonitor = healthMonitor;
        this.metrics = metrics;
    }

    @Override
    public List<RetrievedPost> retrieve(RetrievalQuery query) {
        if (query.allowedSchoolIds().isEmpty()) {
            return List.of();
        }

        long startedAt = System.currentTimeMillis();
        int candidateLimit = Math.max(query.limit(), properties.getCandidateLimit());
        boolean indexAvailable = healthMonitor.isElasticsearchAvailable();
        boolean embeddingAvailable = healthMonitor.isEmbeddingAvailable();
        List<Long> keywordRanks = indexAvailable ? retrieveKeywordRanks(query, candidateLimit) : List.of();
        List<Long> vectorRanks = indexAvailable && embeddingAvailable
                ? retrieveVectorRanks(query, candidateLimit)
                : List.of();

        RrfResult fusion = fuseWithRrf(keywordRanks, vectorRanks, query.limit());
        if (fusion.postIds().isEmpty()) {
            log.info("retrieval requestId={} path=sql-fallback keywordAvailable={} vectorAvailable={} "
                            + "keywordTopIds={} vectorTopIds={} elapsedMs={}",
                    AiRequestContext.requestId(), !keywordRanks.isEmpty(), !vectorRanks.isEmpty(), keywordRanks,
                    vectorRanks, System.currentTimeMillis() - startedAt);
            metrics.recordRetrieval("sql-fallback", System.currentTimeMillis() - startedAt);
            return sqlRetriever.retrieve(query);
        }

        List<PostListItem> posts = postMapper.findNormalPostsByIds(fusion.postIds());
        log.info("retrieval requestId={} path={} keywordTopIds={} vectorTopIds={} rrfScores={} resultIds={} elapsedMs={}",
                AiRequestContext.requestId(), retrievalPath(keywordRanks, vectorRanks), keywordRanks, vectorRanks,
                fusion.scores(), fusion.postIds(), System.currentTimeMillis() - startedAt);
        metrics.recordRetrieval(retrievalPath(keywordRanks, vectorRanks), System.currentTimeMillis() - startedAt);
        return posts.stream().map(RetrievedPost::from).toList();
    }

    private List<Long> retrieveKeywordRanks(RetrievalQuery query, int candidateLimit) {
        try {
            List<Long> ranks = indexClient.searchByKeyword(query.question(), query.allowedSchoolIds(), candidateLimit);
            healthMonitor.recordElasticsearchSuccess();
            return ranks;
        } catch (ResourceAccessException | RestClientResponseException exception) {
            healthMonitor.recordElasticsearchFailure(exception);
            log.warn("retrieval requestId={} branch=keyword status=unavailable reason={}",
                    AiRequestContext.requestId(), exception.getMessage());
            return List.of();
        }
    }

    private List<Long> retrieveVectorRanks(RetrievalQuery query, int candidateLimit) {
        List<Float> vector;
        try {
            vector = embeddingClient.embed(query.question());
            healthMonitor.recordEmbeddingSuccess();
        } catch (ResourceAccessException | RestClientResponseException exception) {
            healthMonitor.recordEmbeddingFailure(exception);
            log.warn("retrieval requestId={} branch=embedding status=unavailable reason={}",
                    AiRequestContext.requestId(), exception.getMessage());
            return List.of();
        }

        try {
            List<Long> ranks = indexClient.searchByVector(vector, query.allowedSchoolIds(), candidateLimit);
            healthMonitor.recordElasticsearchSuccess();
            return ranks;
        } catch (ResourceAccessException | RestClientResponseException exception) {
            healthMonitor.recordElasticsearchFailure(exception);
            log.warn("retrieval requestId={} branch=vector-index status=unavailable reason={}",
                    AiRequestContext.requestId(), exception.getMessage());
            return List.of();
        }
    }

    private String retrievalPath(List<Long> keywordRanks, List<Long> vectorRanks) {
        if (!keywordRanks.isEmpty() && !vectorRanks.isEmpty()) {
            return "hybrid-rrf";
        }
        return keywordRanks.isEmpty() ? "vector-only" : "keyword-only";
    }

    private RrfResult fuseWithRrf(List<Long> keywordRanks, List<Long> vectorRanks, int limit) {
        Map<Long, Double> scores = new HashMap<>();
        addRanks(scores, keywordRanks);
        addRanks(scores, vectorRanks);
        List<Long> postIds = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
        return new RrfResult(postIds, Map.copyOf(scores));
    }

    private void addRanks(Map<Long, Double> scores, List<Long> rankedPostIds) {
        for (int index = 0; index < rankedPostIds.size(); index++) {
            Long postId = rankedPostIds.get(index);
            double score = 1D / (properties.getRrfRankConstant() + index + 1D);
            scores.merge(postId, score, Double::sum);
        }
    }

    private record RrfResult(List<Long> postIds, Map<Long, Double> scores) {
    }
}
