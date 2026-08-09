package com.campuscircle.ai;

import com.campuscircle.post.PostListItem;
import com.campuscircle.post.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Retrieves independent keyword and semantic candidates, then fuses their rank positions with RRF.
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

    public HybridPostRetriever(PostRetrievalService sqlRetriever, ElasticsearchPostIndexClient indexClient,
                               EmbeddingClient embeddingClient, PostMapper postMapper, SearchProperties properties) {
        this.sqlRetriever = sqlRetriever;
        this.indexClient = indexClient;
        this.embeddingClient = embeddingClient;
        this.postMapper = postMapper;
        this.properties = properties;
    }

    @Override
    public List<RetrievedPost> retrieve(RetrievalQuery query) {
        if (query.allowedSchoolIds().isEmpty()) {
            return List.of();
        }
        try {
            int candidateLimit = Math.max(query.limit(), properties.getCandidateLimit());
            List<Long> keywordRanks = indexClient.searchByKeyword(
                    query.question(), query.allowedSchoolIds(), candidateLimit);
            List<Long> vectorRanks = indexClient.searchByVector(
                    embeddingClient.embed(query.question()), query.allowedSchoolIds(), candidateLimit);
            List<Long> fusedIds = fuseWithRrf(keywordRanks, vectorRanks, query.limit());
            if (fusedIds.isEmpty()) {
                return sqlRetriever.retrieve(query);
            }
            List<PostListItem> posts = postMapper.findNormalPostsByIds(fusedIds);
            return posts.stream().map(RetrievedPost::from).toList();
        } catch (RuntimeException exception) {
            log.warn("Hybrid retrieval unavailable; falling back to SQL keyword retrieval: {}", exception.getMessage());
            return sqlRetriever.retrieve(query);
        }
    }

    private List<Long> fuseWithRrf(List<Long> keywordRanks, List<Long> vectorRanks, int limit) {
        Map<Long, Double> scores = new HashMap<>();
        addRanks(scores, keywordRanks);
        addRanks(scores, vectorRanks);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addRanks(Map<Long, Double> scores, List<Long> rankedPostIds) {
        for (int index = 0; index < rankedPostIds.size(); index++) {
            Long postId = rankedPostIds.get(index);
            double score = 1D / (properties.getRrfRankConstant() + index + 1D);
            scores.merge(postId, score, Double::sum);
        }
    }
}
