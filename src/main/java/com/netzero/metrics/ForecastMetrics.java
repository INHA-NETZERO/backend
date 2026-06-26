package com.netzero.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ForecastMetrics {
    private final MeterRegistry registry;
    private final Map<String, AtomicReference<Double>> wapeGauges = new ConcurrentHashMap<>();

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
        wapeGauges.computeIfAbsent(itemCode, code -> {
            AtomicReference<Double> holder = new AtomicReference<>(0.0);
            Gauge.builder("zerowave.forecast.wape", holder, AtomicReference::get)
                 .tag("item", code)
                 .strongReference(true)
                 .register(registry);
            return holder;
        }).set(wape);
    }
}
