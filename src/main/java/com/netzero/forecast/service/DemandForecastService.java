package com.netzero.forecast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netzero.feature.FeatureBuilder;
import com.netzero.forecast.domain.DemandForecast;
import com.netzero.forecast.dto.*;
import com.netzero.forecast.port.ForecastPort;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.order.service.Newsvendor;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.repository.OrderPolicyRepository;
import com.netzero.weather.dto.WeatherSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DemandForecastService {

    private final ForecastPort forecastPort;
    private final FeatureBuilder featureBuilder;
    private final OrderPolicyRepository orderPolicyRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final ObjectMapper objectMapper;

    public DemandForecastService(ForecastPort forecastPort,
                                  FeatureBuilder featureBuilder,
                                  OrderPolicyRepository orderPolicyRepository,
                                  DemandForecastRepository demandForecastRepository,
                                  ObjectMapper objectMapper) {
        this.forecastPort = forecastPort;
        this.featureBuilder = featureBuilder;
        this.orderPolicyRepository = orderPolicyRepository;
        this.demandForecastRepository = demandForecastRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ForecastResponse forecast(Long storeId,
                                      LocalDate targetDate,
                                      List<Long> itemIds,
                                      List<WeatherSnapshot> weather,
                                      List<String> presignedUrls) {
        // Step 1: Build ForecastRow per item and collect metadata
        Map<Long, Map<String, Object>> itemFeatures = new LinkedHashMap<>();
        List<ForecastRow> rows = new ArrayList<>();
        int maxLeadTime = 1;
        int maxCycle = 1;
        int maxCoverage = 1;

        for (Long itemId : itemIds) {
            Map<String, Object> features = featureBuilder.build(storeId, itemId, targetDate);
            itemFeatures.put(itemId, features);

            Optional<OrderPolicy> policyOpt = orderPolicyRepository.findByStoreIdAndItemId(storeId, itemId);
            int orderCycleDays = policyOpt.map(OrderPolicy::getOrderCycleDays).orElse(7);
            int leadTimeDays = policyOpt.map(OrderPolicy::getLeadTimeDays).orElse(1);
            int coverageDays = orderCycleDays + leadTimeDays;

            if (leadTimeDays > maxLeadTime) maxLeadTime = leadTimeDays;
            if (orderCycleDays > maxCycle) maxCycle = orderCycleDays;
            if (coverageDays > maxCoverage) maxCoverage = coverageDays;

            rows.add(new ForecastRow(itemId, orderCycleDays, leadTimeDays, features));
        }

        // Step 2: Build ForecastRequest
        CoverageSpec coverage = new CoverageSpec(maxLeadTime, maxCycle, maxCoverage);
        ForecastRequest req = new ForecastRequest(storeId, targetDate, presignedUrls, coverage, weather, rows);

        // Step 3: Call ForecastPort
        ForecastResponse response = forecastPort.orderRecommendation(req);

        // Step 4: Upsert DemandForecast for each predicted item
        for (ItemForecast itemForecast : response.predictions()) {
            Long itemId = itemForecast.itemId();

            List<Newsvendor.Quantiles> dailyQ = itemForecast.daily().stream()
                    .map(d -> new Newsvendor.Quantiles(d.p10(), d.p50(), d.p90()))
                    .collect(Collectors.toList());
            Newsvendor.Quantiles horizon = Newsvendor.sumDaily(dailyQ);

            String featuresJson;
            try {
                featuresJson = objectMapper.writeValueAsString(
                        itemFeatures.getOrDefault(itemId, Collections.emptyMap()));
            } catch (JsonProcessingException e) {
                featuresJson = "{}";
            }

            DemandForecast df = demandForecastRepository
                    .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, targetDate)
                    .orElse(new DemandForecast());
            df.setStoreId(storeId);
            df.setItemId(itemId);
            df.setTargetDate(targetDate);
            df.setP10(BigDecimal.valueOf(horizon.p10()));
            df.setP50(BigDecimal.valueOf(horizon.p50()));
            df.setP90(BigDecimal.valueOf(horizon.p90()));
            df.setPredictedQuantity(BigDecimal.valueOf(horizon.p50()));
            df.setModelVersion(response.modelVersion());
            df.setFeatures(featuresJson);
            demandForecastRepository.save(df);
        }

        return response;
    }
}
