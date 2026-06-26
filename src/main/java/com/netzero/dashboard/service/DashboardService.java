package com.netzero.dashboard.service;

import com.netzero.carbon.domain.CarbonSaving;
import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.dashboard.dto.DashboardSummary;
import com.netzero.forecast.domain.DemandForecast;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.forecast.service.WapeService;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.dto.RecommendationItem;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.order.service.DueItemSelector;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.domain.SalesRecord;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DueItemSelector dueItemSelector;
    private final OrderRecommendationRepository orderRecommendationRepository;
    private final CarbonSavingRepository carbonSavingRepository;
    private final ItemMasterRepository itemMasterRepository;
    private final OrderPolicyRepository orderPolicyRepository;
    private final WapeService wapeService;
    private final SalesRecordRepository salesRecordRepository;
    private final DemandForecastRepository demandForecastRepository;

    public DashboardService(
            OrderPolicyRepository orderPolicyRepository,
            InventorySnapshotRepository inventorySnapshotRepository,
            OrderRecommendationRepository orderRecommendationRepository,
            CarbonSavingRepository carbonSavingRepository,
            ItemMasterRepository itemMasterRepository,
            WapeService wapeService,
            SalesRecordRepository salesRecordRepository,
            DemandForecastRepository demandForecastRepository) {

        this.dueItemSelector = new DueItemSelector(orderPolicyRepository, inventorySnapshotRepository);
        this.orderRecommendationRepository = orderRecommendationRepository;
        this.carbonSavingRepository = carbonSavingRepository;
        this.itemMasterRepository = itemMasterRepository;
        this.orderPolicyRepository = orderPolicyRepository;
        this.wapeService = wapeService;
        this.salesRecordRepository = salesRecordRepository;
        this.demandForecastRepository = demandForecastRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long storeId) {
        LocalDate today = LocalDate.now(KST).minusYears(1);

        // Latest target date with a recommendation
        LocalDate latestTargetDate = orderRecommendationRepository
                .findFirstByStoreIdOrderByTargetDateDesc(storeId)
                .map(OrderRecommendation::getTargetDate)
                .orElse(null);

        // Due items count for today
        int dueItemCount = dueItemSelector.select(storeId, today).due().size();

        // Today's recommendations mapped to DTOs
        List<OrderRecommendation> recs = orderRecommendationRepository
                .findByStoreIdAndTargetDate(storeId, today);

        List<RecommendationItem> recommendedOrders = recs.stream()
                .map(rec -> {
                    ItemMaster item = itemMasterRepository.findById(rec.getItemId()).orElse(null);
                    Optional<OrderPolicy> policy = orderPolicyRepository
                            .findByStoreIdAndItemId(storeId, rec.getItemId());
                    return new RecommendationItem(
                            rec.getItemId(),
                            item != null ? item.getName() : null,
                            rec.getRecommendedQuantity(),
                            rec.getActualQuantity(),
                            rec.getCriticalRatio(),
                            rec.getExpectedWasteAvoidedKg(),
                            policy.map(OrderPolicy::getOrderLotUnit).orElse(null));
                })
                .collect(Collectors.toList());

        // Today's carbon savings (guaranteed + potential)
        List<CarbonSaving> carbons = carbonSavingRepository.findByStoreIdAndTargetDate(storeId, today);
        BigDecimal carbonToday = carbons.stream()
                .flatMap(cs -> List.of(
                        cs.getGuaranteedSavingKg(),
                        cs.getPotentialSavingKg()).stream())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Compute WAPE per item for today
        Map<String, Double> wapeByItem = new HashMap<>();
        for (OrderRecommendation rec : recs) {
            ItemMaster item = itemMasterRepository.findById(rec.getItemId()).orElse(null);
            if (item == null) {
                continue;
            }

            // Get actual sales for today
            List<SalesRecord> sales = salesRecordRepository
                    .findByStoreIdAndBusinessDateBetween(storeId, today, today);
            SalesRecord todaysSale = sales.stream()
                    .filter(s -> s.getItemId().equals(rec.getItemId()))
                    .findFirst()
                    .orElse(null);

            // Get forecast for today
            Optional<DemandForecast> forecast = demandForecastRepository
                    .findByStoreIdAndItemIdAndTargetDate(storeId, rec.getItemId(), today);

            // Compute WAPE if both data available
            if (todaysSale != null && forecast.isPresent()) {
                DemandForecast df = forecast.get();
                List<Double> actual = List.of(todaysSale.getQuantitySold().doubleValue());
                List<Double> predicted = List.of(
                    df.getP50() != null ? df.getP50().doubleValue() : 0.0);

                double wape = wapeService.computeAndRecord(item.getName(), actual, predicted);
                wapeByItem.put(item.getName(), wape);
            }
        }

        return new DashboardSummary(latestTargetDate, dueItemCount, recommendedOrders, carbonToday, wapeByItem);
    }
}
