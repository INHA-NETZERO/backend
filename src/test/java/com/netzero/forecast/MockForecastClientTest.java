package com.netzero.forecast;

import com.netzero.forecast.dto.*;
import com.netzero.forecast.port.ForecastPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MockForecastClientTest {
    @Autowired
    ForecastPort forecastPort;

    @Test
    void mockReturnsEightQuantilesWhenCoverageDaysIsEight() {
        LocalDate targetDate = LocalDate.now();
        CoverageSpec coverage = new CoverageSpec(7, 1, 8);
        List<ForecastRow> rows = List.of(
            new ForecastRow(1L, 1, 7, Map.of("ma7", 10.0))
        );
        ForecastRequest request = new ForecastRequest(
            1L,
            targetDate,
            new SalesHistory(List.of(), "sales_csv_v1"),
            coverage,
            List.of(),
            rows
        );

        ForecastResponse result = forecastPort.orderRecommendation(request);

        assertThat(result.predictions()).hasSize(1);
        assertThat(result.predictions().get(0).daily()).hasSize(8);

        // Verify p10 <= p50 <= p90 for each quantile
        for (DailyQuantile quantile : result.predictions().get(0).daily()) {
            assertThat(quantile.p10()).isLessThanOrEqualTo(quantile.p50());
            assertThat(quantile.p50()).isLessThanOrEqualTo(quantile.p90());
        }

        // Verify model version
        assertThat(result.modelVersion()).isEqualTo("baseline_v1");
    }
}
