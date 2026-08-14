package com.uninook.ai;

import com.uninook.post.PostListItem;
import com.uninook.post.PostMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoldenRetrievalEvaluationTests {

    private static final Logger log = LoggerFactory.getLogger(GoldenRetrievalEvaluationTests.class);

    @Test
    void reportsTopKHitRateForGoldenRetrievalSet() throws IOException {
        List<GoldenCase> cases = loadCases();
        List<CorpusPost> corpus = loadCorpus();
        PostMapper postMapper = mock(PostMapper.class);
        when(postMapper.findPostsBySchoolIdsAndKeyword(eq(List.of(1L)), anyString(), eq(5)))
                .thenAnswer(invocation -> matchingPosts(corpus, invocation.getArgument(1)));
        PostRetriever retriever = new PostRetrievalService(postMapper);

        long hits = cases.stream()
                .filter(testCase -> retriever.retrieve(new RetrievalQuery(testCase.query(), List.of(1L), 5)).stream()
                        .map(RetrievedPost::id)
                        .anyMatch(testCase.expectedPostId()::equals))
                .count();
        double hitRate = (double) hits / cases.size();
        log.info("golden_retrieval_evaluation cases={} hits={} topKHitRate={}", cases.size(), hits, hitRate);

        assertThat(cases).hasSizeBetween(30, 50);
        assertThat(cases).allSatisfy(testCase -> assertThat(testCase.answerPoint()).isNotBlank());
        assertThat(hitRate).isEqualTo(1D);
    }

    private List<GoldenCase> loadCases() throws IOException {
        try (BufferedReader reader = resourceReader("/ai/golden-retrieval.csv")) {
            return reader.lines()
                    .skip(1)
                    .map(line -> line.split(",", 3))
                    .map(columns -> new GoldenCase(columns[0], Long.parseLong(columns[1]), columns[2]))
                    .toList();
        }
    }

    private List<CorpusPost> loadCorpus() throws IOException {
        try (BufferedReader reader = resourceReader("/ai/golden-retrieval-corpus.csv")) {
            return reader.lines()
                    .skip(1)
                    .map(line -> line.split(",", 3))
                    .map(columns -> new CorpusPost(Long.parseLong(columns[0]), columns[1], columns[2]))
                    .toList();
        }
    }

    private BufferedReader resourceReader(String resourcePath) {
        return new BufferedReader(new InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)), StandardCharsets.UTF_8));
    }

    private List<PostListItem> matchingPosts(List<CorpusPost> corpus, String keyword) {
        String compactKeyword = compact(keyword);
        return corpus.stream()
                .filter(post -> compact(post.title() + post.content()).contains(compactKeyword))
                .map(post -> new PostListItem(post.id(), post.title(), post.content(),
                        1L, "Example University", "Example Campus", "Example City", 1L, "Campus", "campus",
                        1L, "tester", null, 0, 0, 0, 0D, LocalDateTime.now()))
                .toList();
    }

    private String compact(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private record GoldenCase(String query, Long expectedPostId, String answerPoint) {
    }

    private record CorpusPost(Long id, String title, String content) {
    }
}
