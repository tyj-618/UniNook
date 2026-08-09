package com.campuscircle.ai;

import com.campuscircle.event.PostSearchIndexEvent;
import com.campuscircle.post.PostDetail;
import com.campuscircle.post.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(PostSearchIndexService.class);
    private static final int REINDEX_BATCH_SIZE = 100;

    private final SearchProperties properties;
    private final PostMapper postMapper;
    private final PostSearchDocumentBuilder documentBuilder;
    private final EmbeddingClient embeddingClient;
    private final ObjectProvider<ElasticsearchPostIndexClient> indexClientProvider;

    public PostSearchIndexService(SearchProperties properties, PostMapper postMapper,
                                  PostSearchDocumentBuilder documentBuilder, EmbeddingClient embeddingClient,
                                  ObjectProvider<ElasticsearchPostIndexClient> indexClientProvider) {
        this.properties = properties;
        this.postMapper = postMapper;
        this.documentBuilder = documentBuilder;
        this.embeddingClient = embeddingClient;
        this.indexClientProvider = indexClientProvider;
    }

    /**
     * Reconciles from MySQL instead of replaying event payload, making duplicated or out-of-order events harmless.
     */
    public void reconcile(PostSearchIndexEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        ElasticsearchPostIndexClient indexClient = indexClientProvider.getIfAvailable();
        if (indexClient == null) {
            return;
        }

        try {
            PostDetail post = postMapper.findDetailById(event.postId()).orElse(null);
            if (post == null || post.status() != 0) {
                indexClient.delete(event.postId());
                return;
            }
            String searchText = documentBuilder.buildSearchText(post);
            indexClient.upsert(documentBuilder.build(post, embeddingClient.embed(searchText)));
        } catch (RuntimeException exception) {
            log.warn("Failed to reconcile Elasticsearch document for post {}", event.postId(), exception);
        }
    }

    public int rebuildAll() {
        if (!properties.isEnabled()) {
            return 0;
        }
        int offset = 0;
        int indexed = 0;
        while (true) {
            List<Long> postIds = postMapper.findNormalPostIdsForIndex(REINDEX_BATCH_SIZE, offset);
            for (Long postId : postIds) {
                reconcile(PostSearchIndexEvent.forPost(postId));
                indexed++;
            }
            if (postIds.size() < REINDEX_BATCH_SIZE) {
                return indexed;
            }
            offset += postIds.size();
        }
    }
}
