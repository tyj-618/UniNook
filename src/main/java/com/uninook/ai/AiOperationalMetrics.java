package com.uninook.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AiOperationalMetrics {

    private final MeterRegistry meterRegistry;

    public AiOperationalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private AiOperationalMetrics() {
        this.meterRegistry = null;
    }

    static AiOperationalMetrics noOp() {
        return new AiOperationalMetrics();
    }

    void recordModelCall(String mode, String outcome, long elapsedMs, Integer inputTokens, Integer outputTokens) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("uninook.ai.model.calls")
                .tag("mode", mode)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("uninook.ai.model.duration")
                .tag("mode", mode)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
        recordTokens("input", inputTokens);
        recordTokens("output", outputTokens);
    }

    void recordSessionConflict() {
        if (meterRegistry != null) {
            Counter.builder("uninook.ai.session.conflicts").register(meterRegistry).increment();
        }
    }

    void recordRetrieval(String path, long elapsedMs) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("uninook.ai.retrieval.calls")
                .tag("path", path)
                .register(meterRegistry)
                .increment();
        Timer.builder("uninook.ai.retrieval.duration")
                .tag("path", path)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    private void recordTokens(String direction, Integer tokens) {
        if (tokens != null && tokens >= 0) {
            Counter.builder("uninook.ai.model.tokens")
                    .tag("direction", direction)
                    .register(meterRegistry)
                    .increment(tokens);
        }
    }
}
