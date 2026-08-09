package com.campuscircle.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local embedding used only to exercise the indexing pipeline.
 * A real embedding provider must be configured for semantic retrieval quality.
 */
@Component
@ConditionalOnProperty(prefix = "campuscircle.search", name = "embedding-provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingClient implements EmbeddingClient {

    private final SearchProperties properties;

    public MockEmbeddingClient(SearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Float> embed(String text) {
        int dimensions = properties.getEmbeddingDimensions();
        if (dimensions < 8) {
            throw new IllegalStateException("Embedding dimensions must be at least 8");
        }

        float[] values = new float[dimensions];
        String normalized = text == null ? "" : text.trim().toLowerCase();
        for (int index = 0; index < normalized.length(); index++) {
            int codePoint = normalized.codePointAt(index);
            int bucket = Math.floorMod(31 * codePoint + index, dimensions);
            values[bucket] += (codePoint & 1) == 0 ? 1F : -1F;
        }

        double norm = 0D;
        for (float value : values) {
            norm += value * value;
        }
        if (norm > 0D) {
            float divisor = (float) Math.sqrt(norm);
            for (int index = 0; index < values.length; index++) {
                values[index] /= divisor;
            }
        }

        List<Float> vector = new ArrayList<>(dimensions);
        for (float value : values) {
            vector.add(value);
        }
        return vector;
    }
}
