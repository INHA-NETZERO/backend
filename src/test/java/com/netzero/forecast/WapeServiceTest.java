package com.netzero.forecast;

import com.netzero.forecast.service.WapeService;
import com.netzero.metrics.ForecastMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WapeServiceTest {

    private WapeService service;

    @BeforeEach
    void setUp() {
        service = new WapeService(new ForecastMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void wape_knownValues() {
        // Σ|10-9| + |8-9| = 1+1 = 2; Σactual = 18; WAPE = 2/18 = 0.111...
        double result = service.wape(List.of(10.0, 8.0), List.of(9.0, 9.0));
        assertThat(result).isCloseTo(0.111, Offset.offset(0.001));
    }

    @Test
    void wape_perfectPrediction_returnsZero() {
        double result = service.wape(List.of(5.0, 10.0), List.of(5.0, 10.0));
        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void wape_emptyLists_returnsZero() {
        double result = service.wape(List.of(), List.of());
        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void wape_zeroActual_returnsZero() {
        double result = service.wape(List.of(0.0, 0.0), List.of(5.0, 5.0));
        assertThat(result).isEqualTo(0.0);
    }

    @Test
    void computeAndRecord_recordsWapeMetric() {
        var registry = new SimpleMeterRegistry();
        var svc = new WapeService(new ForecastMetrics(registry));

        double w = svc.computeAndRecord("우유", List.of(10.0, 8.0), List.of(9.0, 9.0));

        assertThat(w).isCloseTo(0.111, Offset.offset(0.001));
        double gaugeVal = registry.get("zerowave.forecast.wape")
                                  .tag("item", "우유")
                                  .gauge().value();
        assertThat(gaugeVal).isCloseTo(0.111, Offset.offset(0.001));
    }
}
