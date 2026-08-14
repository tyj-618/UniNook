package com.uninook.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOperationalMetricsTests {

    @Test
    void recordsModelRetrievalAndSessionMetricsWithoutQuestionContent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiOperationalMetrics metrics = new AiOperationalMetrics(registry);

        metrics.recordModelCall("tools", "success", 15, 12, 8);
        metrics.recordRetrieval("hybrid-rrf", 6);
        metrics.recordSessionConflict();

        assertThat(registry.get("uninook.ai.model.calls").counter().count()).isEqualTo(1D);
        assertThat(registry.get("uninook.ai.model.tokens").tag("direction", "input").counter().count()).isEqualTo(12D);
        assertThat(registry.get("uninook.ai.model.tokens").tag("direction", "output").counter().count()).isEqualTo(8D);
        assertThat(registry.get("uninook.ai.retrieval.calls").tag("path", "hybrid-rrf").counter().count()).isEqualTo(1D);
        assertThat(registry.get("uninook.ai.session.conflicts").counter().count()).isEqualTo(1D);
    }
}
