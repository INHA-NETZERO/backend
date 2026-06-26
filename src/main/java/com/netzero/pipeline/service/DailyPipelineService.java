package com.netzero.pipeline.service;

import com.netzero.carbon.domain.CarbonSaving;
import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.forecast.dto.ForecastResponse;
import com.netzero.forecast.service.DemandForecastService;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.service.DueItemSelector;
import com.netzero.order.service.OrderOptimizationService;
import com.netzero.pipeline.dto.PipelineResult;
import com.netzero.export.service.PresignService;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import com.netzero.weather.dto.WeatherSnapshot;
import com.netzero.weather.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DailyPipelineService {

    private static final Logger log = LoggerFactory.getLogger(DailyPipelineService.class);

    private final DueItemSelector dueItemSelector;
    private final PresignService presignService;
    private final DemandForecastService demandForecastService;
    private final OrderOptimizationService orderOptimizationService;
    private final CarbonSavingRepository carbonSavingRepository;

    @Autowired(required = false)
    private WeatherService weatherService;

    /** Guards against concurrent pipeline runs for the same store+date. */
    private final Set<String> runningKeys = ConcurrentHashMap.newKeySet();

    public DailyPipelineService(
            OrderPolicyRepository orderPolicyRepository,
            InventorySnapshotRepository inventorySnapshotRepository,
            PresignService presignService,
            DemandForecastService demandForecastService,
            OrderOptimizationService orderOptimizationService,
            CarbonSavingRepository carbonSavingRepository) {

        this.dueItemSelector = new DueItemSelector(orderPolicyRepository, inventorySnapshotRepository);
        this.presignService = presignService;
        this.demandForecastService = demandForecastService;
        this.orderOptimizationService = orderOptimizationService;
        this.carbonSavingRepository = carbonSavingRepository;
    }

    /**
     * Runs the full pipeline for a given store and target date.
     *
     * Execution order (strict):
     * 1. DueItemSelector.select()         → identify items due for ordering
     * 2. WeatherService.coverageWeather() → fetch weather for coverage window (optional)
     * 3. PresignService.recentSalesUrls() → presigned S3 URLs for recent sales CSVs
     * 4. DemandForecastService.forecast() → call AI forecast, persist DemandForecast records
     * 5. OrderOptimizationService.optimize() → read saved DemandForecast, produce
     *                                          OrderRecommendation + CarbonSaving records
     */
    public PipelineResult run(Long storeId, LocalDate targetDate) {
        String key = storeId + ":" + targetDate;
        if (!runningKeys.add(key)) {
            throw new ApiException(ErrorCode.PIPELINE_ALREADY_RUNNING);
        }

        long startMs = System.currentTimeMillis();
        try {
            // Step 1 — due item selection
            DueItemSelector.DueSelection selection = dueItemSelector.select(storeId, targetDate);
            List<Long> dueItemIds = selection.due().stream()
                    .map(DueItemSelector.DueItem::itemId)
                    .collect(Collectors.toList());

            log.info("Pipeline storeId={} date={}: {} due item(s)", storeId, targetDate, dueItemIds.size());

            if (dueItemIds.isEmpty()) {
                return new PipelineResult(storeId, targetDate, 0, 0, 0, 0, null,
                        System.currentTimeMillis() - startMs);
            }

            // Step 2 — weather (optional; skipped when KMA is disabled)
            List<WeatherSnapshot> weather = Collections.emptyList();
            if (weatherService != null) {
                int maxCoverage = selection.due().stream()
                        .mapToInt(item -> item.orderCycleDays() + item.leadTimeDays())
                        .max()
                        .orElse(8);
                weather = weatherService.coverageWeather(storeId, targetDate, maxCoverage);
            }

            // Step 3 — presigned sales URLs
            List<String> presignedUrls = presignService.recentSalesUrls(storeId, targetDate, 3);

            // Step 4 — demand forecast (saves DemandForecast records)
            ForecastResponse forecastResponse = demandForecastService.forecast(
                    storeId, targetDate, dueItemIds, weather, presignedUrls);

            // Step 5 — order optimization (reads saved DemandForecast, saves OrderRecommendation + CarbonSaving)
            List<OrderRecommendation> recs = orderOptimizationService.optimize(
                    storeId, targetDate, dueItemIds);

            List<CarbonSaving> carbons = carbonSavingRepository.findByStoreIdAndTargetDate(storeId, targetDate);

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("Pipeline storeId={} date={} complete: forecasted={} recommended={} carbon={} {}ms",
                    storeId, targetDate,
                    forecastResponse.predictions().size(), recs.size(), carbons.size(), elapsedMs);

            return new PipelineResult(
                    storeId, targetDate,
                    dueItemIds.size(),
                    forecastResponse.predictions().size(),
                    recs.size(),
                    carbons.size(),
                    forecastResponse.modelVersion(),
                    elapsedMs);

        } finally {
            runningKeys.remove(key);
        }
    }
}
