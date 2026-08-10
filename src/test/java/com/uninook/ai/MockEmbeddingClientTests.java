package com.uninook.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingClientTests {

    @Test
    void producesDeterministicNormalizedVectors() {
        SearchProperties properties = new SearchProperties();
        properties.setEmbeddingDimensions(16);
        MockEmbeddingClient client = new MockEmbeddingClient(properties);

        List<Float> first = client.embed("鼓楼校区图书馆自习");
        List<Float> second = client.embed("鼓楼校区图书馆自习");

        double squaredNorm = first.stream().mapToDouble(value -> value * value).sum();
        assertThat(first).hasSize(16).containsExactlyElementsOf(second);
        assertThat(squaredNorm).isCloseTo(1D, org.assertj.core.data.Offset.offset(0.0001D));
    }
}
