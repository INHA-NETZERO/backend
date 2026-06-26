package com.netzero.forecast.port;

import com.netzero.forecast.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
class MockForecastConfig {
    @Bean
    @ConditionalOnMissingBean(ForecastPort.class)
    ForecastPort mockForecastClient() {
        return (req) -> {
            var items = req.rows().stream().map(row -> {
                var daily = new ArrayList<DailyQuantile>();

                // Generate quantiles for each day from targetDate+1 to targetDate+coverageDays
                for (int i = 1; i <= req.coverage().coverageDays(); i++) {
                    LocalDate date = req.targetDate().plusDays(i);

                    // Get ma7 from features (moving average of 7 days)
                    double ma7 = 10.0; // default value
                    if (row.features() != null && row.features().containsKey("ma7")) {
                        Object ma7Obj = row.features().get("ma7");
                        if (ma7Obj instanceof Number) {
                            ma7 = ((Number) ma7Obj).doubleValue();
                        }
                    }

                    // Calculate quantiles: p10=0.8*p50, p90=1.3*p50
                    double p50 = ma7;
                    double p10 = 0.8 * p50;
                    double p90 = 1.3 * p50;

                    daily.add(new DailyQuantile(date, p10, p50, p90));
                }

                return new ItemForecast(row.itemId(), daily);
            }).collect(Collectors.toList());

            return new ForecastResponse("baseline_v1", items);
        };
    }
}
