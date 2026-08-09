package com.campuscircle.ai;

import java.util.List;

/**
 * Converts one searchable text into a normalized dense vector.
 */
public interface EmbeddingClient {

    List<Float> embed(String text);
}
