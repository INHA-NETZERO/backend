package com.netzero.order;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.netzero.order.service.QuantileInterpolator.interpolate;

class QuantileInterpolatorTest {

    @Test
    void midBandBetweenP10AndP50() {
        // CR=0.421 → fraction=(0.421-0.10)/0.40=0.8025 → 60+0.8025*(80-60)=76.05
        assertThat(interpolate(60, 80, 108, 0.421)).isCloseTo(76.05, within(0.05));
    }

    @Test
    void upperBandBetweenP50AndP90() {
        // CR=0.70 → fraction=(0.70-0.50)/0.40=0.5 → 80+0.5*(108-80)=94
        assertThat(interpolate(60, 80, 108, 0.70)).isCloseTo(94.0, within(0.001));
    }

    @Test
    void clampLowToP10() {
        assertThat(interpolate(60, 80, 108, 0.05)).isEqualTo(60);
    }

    @Test
    void clampHighToP90() {
        assertThat(interpolate(60, 80, 108, 0.97)).isEqualTo(108);
    }

    static org.assertj.core.data.Offset<Double> within(double d) {
        return org.assertj.core.data.Offset.offset(d);
    }
}
