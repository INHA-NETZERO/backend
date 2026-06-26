package com.netzero.order;

import com.netzero.order.service.Newsvendor;
import com.netzero.order.service.Newsvendor.Quantiles;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class NewsvendorTest {

    @Test
    void criticalRatio() {
        assertThat(Newsvendor.criticalRatio(800, 1100)).isCloseTo(0.421, offset(0.001));
    }

    @Test
    void sumDailyAggregates() {
        var sum = Newsvendor.sumDaily(List.of(new Quantiles(6, 8, 11), new Quantiles(7, 9, 12)));
        assertThat(sum.p10()).isEqualTo(13);
        assertThat(sum.p50()).isEqualTo(17);
        assertThat(sum.p90()).isEqualTo(23);
    }

    @Test
    void roundUpToLot() {
        assertThat(Newsvendor.roundToLot(64.05, 2)).isEqualTo(66);
    }

    @Test
    void recommendedOrderMilkScenario() {
        var horizon = new Quantiles(60, 80, 108); // 8일 합산
        double order = Newsvendor.recommendedOrder(horizon, 800, 1100, 12, 2); // Q*=76.05, -12=64.05, lot2→66
        assertThat(order).isEqualTo(66);
    }
}
