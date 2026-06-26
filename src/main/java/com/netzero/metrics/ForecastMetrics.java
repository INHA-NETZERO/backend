package com.netzero.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ForecastMetrics {
    private final MeterRegistry registry;

    public ForecastMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLlmCall(int tokens, long latencyMs, boolean cacheHit) {
        registry.counter("zerowave.llm.calls").increment();
        registry.counter("zerowave.llm.tokens").increment(tokens);
        registry.timer("zerowave.llm.latency").record(latencyMs, TimeUnit.MILLISECONDS);
        if (cacheHit) {
            registry.counter("zerowave.llm.cache.hit").increment();
        } else {
            registry.counter("zerowave.llm.cache.miss").increment();
        }
    }

    public void recordPipeline(long ms) {
        registry.timer("zerowave.pipeline.duration").record(ms, TimeUnit.MILLISECONDS);
    }

    public void recordWape(String itemCode, double wape) {
        registry.gauge("zerowave.forecast.wape",
            Tags.of("item", itemCode), wape);
    }
}
