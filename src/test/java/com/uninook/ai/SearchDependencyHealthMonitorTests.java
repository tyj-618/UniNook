package com.uninook.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchDependencyHealthMonitorTests {

    @Test
    void marksDependencyUnavailableAfterConfiguredConsecutiveFailuresAndRecovers() {
        ElasticsearchPostIndexClient indexClient = mock(ElasticsearchPostIndexClient.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        SearchProperties properties = new SearchProperties();
        properties.setHealthFailureThreshold(2);
        SearchDependencyHealthMonitor monitor = new SearchDependencyHealthMonitor(indexClient, embeddingClient, properties);

        doThrow(new ResourceAccessException("connection refused")).when(indexClient).ping();
        monitor.probeElasticsearch();
        assertThat(monitor.isElasticsearchAvailable()).isTrue();
        monitor.probeElasticsearch();
        assertThat(monitor.isElasticsearchAvailable()).isFalse();

        doNothing().when(indexClient).ping();
        monitor.probeElasticsearch();
        assertThat(monitor.isElasticsearchAvailable()).isTrue();
    }

    @Test
    void independentlyTracksEmbeddingHealthAndRecovery() {
        ElasticsearchPostIndexClient indexClient = mock(ElasticsearchPostIndexClient.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        SearchProperties properties = new SearchProperties();
        properties.setHealthFailureThreshold(2);
        SearchDependencyHealthMonitor monitor = new SearchDependencyHealthMonitor(indexClient, embeddingClient, properties);

        when(embeddingClient.embed("uninook search health probe"))
                .thenThrow(new ResourceAccessException("embedding timeout"))
                .thenThrow(new ResourceAccessException("embedding timeout"))
                .thenReturn(List.of(0.1F));
        monitor.probeEmbedding();
        monitor.probeEmbedding();
        assertThat(monitor.isEmbeddingAvailable()).isFalse();

        monitor.probeEmbedding();
        assertThat(monitor.isEmbeddingAvailable()).isTrue();
    }
}
