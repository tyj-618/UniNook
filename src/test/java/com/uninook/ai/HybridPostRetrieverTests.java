package com.uninook.ai;

import com.uninook.post.PostListItem;
import com.uninook.post.PostMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridPostRetrieverTests {

    @Test
    void fusesKeywordAndVectorRanksWithRrfBeforeReadingCurrentPostsFromMysql() {
        PostRetrievalService sqlRetriever = mock(PostRetrievalService.class);
        ElasticsearchPostIndexClient indexClient = mock(ElasticsearchPostIndexClient.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        PostMapper postMapper = mock(PostMapper.class);
        SearchProperties properties = new SearchProperties();
        properties.setCandidateLimit(3);
        properties.setRrfRankConstant(60);

        HybridPostRetriever retriever = new HybridPostRetriever(
                sqlRetriever, indexClient, embeddingClient, postMapper, properties);
        RetrievalQuery query = new RetrievalQuery("哪里适合自习", List.of(10L), 3);

        when(embeddingClient.embed(query.question())).thenReturn(List.of(0.1F, 0.2F));
        when(indexClient.searchByKeyword(query.question(), query.allowedSchoolIds(), 3)).thenReturn(List.of(1L, 2L));
        when(indexClient.searchByVector(List.of(0.1F, 0.2F), query.allowedSchoolIds(), 3)).thenReturn(List.of(2L, 3L));
        when(postMapper.findNormalPostsByIds(List.of(2L, 1L, 3L))).thenReturn(List.of(post(2L), post(1L), post(3L)));

        List<RetrievedPost> result = retriever.retrieve(query);

        assertThat(result).extracting(RetrievedPost::id).containsExactly(2L, 1L, 3L);
    }

    @Test
    void fallsBackToSqlRetrievalWhenElasticsearchIsUnavailable() {
        PostRetrievalService sqlRetriever = mock(PostRetrievalService.class);
        ElasticsearchPostIndexClient indexClient = mock(ElasticsearchPostIndexClient.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        PostMapper postMapper = mock(PostMapper.class);
        SearchProperties properties = new SearchProperties();
        RetrievalQuery query = new RetrievalQuery("图书馆", List.of(10L), 3);
        List<RetrievedPost> fallback = List.of(new RetrievedPost(9L, "图书馆", "内容", "南京大学", LocalDateTime.now()));

        when(indexClient.searchByKeyword(query.question(), query.allowedSchoolIds(), properties.getCandidateLimit()))
                .thenThrow(new IllegalStateException("Elasticsearch unavailable"));
        when(sqlRetriever.retrieve(query)).thenReturn(fallback);

        HybridPostRetriever retriever = new HybridPostRetriever(
                sqlRetriever, indexClient, embeddingClient, postMapper, properties);

        assertThat(retriever.retrieve(query)).isEqualTo(fallback);
    }

    private PostListItem post(Long id) {
        return new PostListItem(id, "标题" + id, "正文", 10L, "南京大学", "鼓楼校区", "南京市",
                1L, "课程交流", "course", 1L, "用户", null, 0, 0, 0, 0D, LocalDateTime.now());
    }
}
