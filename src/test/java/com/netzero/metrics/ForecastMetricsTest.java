package com.netzero.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastMetricsTest {
    @Test
    void recordLlmCall_incrementsCountersAndTimer() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ForecastMetrics(registry);

        metrics.recordLlmCall(100, 200L, false);

        assertThat(registry.counter("zerowave.llm.calls").count()).isEqualTo(1.0);
        assertThat(registry.counter("zerowave.llm.tokens").count()).isEqualTo(100.0);
        assertThat(registry.counter("zerowave.llm.cache.miss").count()).isEqualTo(1.0);
        assertThat(registry.counter("zerowave.llm.cache.hit").count()).isEqualTo(0.0);
    }

    @Test
    void recordLlmCall_cacheHit_incrementsHitCounter() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ForecastMetrics(registry);

        metrics.recordLlmCall(50, 10L, true);

        assertThat(registry.counter("zerowave.llm.cache.hit").count()).isEqualTo(1.0);
        assertThat(registry.counter("zerowave.llm.cache.miss").count()).isEqualTo(0.0);
    }

    @Test
    void recordPipeline_recordsTimerDuration() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ForecastMetrics(registry);

        metrics.recordPipeline(500L);

        assertThat(registry.timer("zerowave.pipeline.duration").count()).isEqualTo(1L);
    }
}
