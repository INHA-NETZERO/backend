package com.netzero.order.service;

import com.netzero.carbon.domain.CarbonSaving;
import com.netzero.carbon.dto.*;
import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.carbon.service.CarbonAccountingService;
import com.netzero.carbon.dto.CarbonResult;
import com.netzero.forecast.domain.DemandForecast;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.dto.RecommendationItem;
import com.netzero.order.dto.RecommendationResponse;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.domain.SalesRecord;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderOptimizationService {

    private final DemandForecastRepository demandForecastRepository;
    private final OrderPolicyRepository orderPolicyRepository;
    private final ItemMasterRepository itemMasterRepository;
    private final InventorySnapshotRepository inventorySnapshotRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final OrderRecommendationRepository orderRecommendationRepository;
    private final CarbonSavingRepository carbonSavingRepository;
    private final CarbonAccountingService carbonAccountingService = new CarbonAccountingService();

    @Value("${optimization.default-cu:1.0}")
    private double defaultCu;

    @Value("${optimization.default-co:1.0}")
    private double defaultCo;

    @Value("${carbon.car-kgco2-per-km:4.6}")
    private double carKgco2PerKm;

    public OrderOptimizationService(DemandForecastRepository demandForecastRepository,
                                     OrderPolicyRepository orderPolicyRepository,
                                     ItemMasterRepository itemMasterRepository,
                                     InventorySnapshotRepository inventorySnapshotRepository,
                                     SalesRecordRepository salesRecordRepository,
                                     OrderRecommendationRepository orderRecommendationRepository,
                                     CarbonSavingRepository carbonSavingRepository) {
        this.demandForecastRepository = demandForecastRepository;
        this.orderPolicyRepository = orderPolicyRepository;
        this.itemMasterRepository = itemMasterRepository;
        this.inventorySnapshotRepository = inventorySnapshotRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.orderRecommendationRepository = orderRecommendationRepository;
        this.carbonSavingRepository = carbonSavingRepository;
    }

    @Transactional
    public List<OrderRecommendation> optimize(Long storeId, LocalDate targetDate, List<Long> itemIds) {
        List<OrderRecommendation> result = new ArrayList<>();

        for (Long itemId : itemIds) {
            // 1. Load forecast (skip if missing)
            DemandForecast df = demandForecastRepository
                    .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, targetDate)
                    .orElse(null);
            if (df == null) continue;

            // 2. Load policy and item master
            Optional<OrderPolicy> policyOpt = orderPolicyRepository.findByStoreIdAndItemId(storeId, itemId);
            ItemMaster item = itemMasterRepository.findById(itemId).orElse(null);
            if (item == null) continue;

            // 3. Compute cu / co
            double purchasePrice = item.getPurchasePrice() != null
                    ? item.getPurchasePrice().doubleValue() : 0.0;
            double cu, co;
            if (purchasePrice > 0) {
                cu = purchasePrice * 0.3;
                co = purchasePrice;
            } else {
                cu = defaultCu;
                co = defaultCo;
            }

            // 4. Build horizon quantiles from saved forecast
            Newsvendor.Quantiles horizon = new Newsvendor.Quantiles(
                    df.getP10().doubleValue(),
                    df.getP50().doubleValue(),
                    df.getP90().doubleValue());

            // 5. Get on-hand inventory
            double onHand = inventorySnapshotRepository
                    .findByStoreIdAndItem_IdAndBusinessDate(storeId, itemId, targetDate)
                    .map(inv -> inv.getClosingStock() != null ? inv.getClosingStock().doubleValue() : 0.0)
                    .orElse(0.0);

            // 6. Get lot size
            double lot = policyOpt
                    .map(p -> p.getOrderLotUnit() != null ? p.getOrderLotUnit().doubleValue() : 1.0)
                    .orElse(1.0);

            // 7. Newsvendor calculations
            double optimalStock = Newsvendor.optimalStock(horizon, cu, co);
            double recommendedOrder = Newsvendor.recommendedOrder(horizon, cu, co, onHand, lot);
            double cr = Newsvendor.criticalRatio(cu, co);

            // 8. Baseline = avg same-day-of-week sales (past 28 days) * coverageDays
            int coverageDays = policyOpt
                    .map(p -> p.getOrderCycleDays() + p.getLeadTimeDays())
                    .orElse(8);
            DayOfWeek targetDow = targetDate.getDayOfWeek();

            List<SalesRecord> pastSales = salesRecordRepository
                    .findByStoreIdAndBusinessDateBetween(storeId, targetDate.minusDays(28), targetDate.minusDays(1));

            OptionalDouble avgSameDow = pastSales.stream()
                    .filter(s -> s.getItemId().equals(itemId) && s.getBusinessDate().getDayOfWeek() == targetDow)
                    .mapToDouble(s -> s.getQuantitySold().doubleValue())
                    .average();

            double baseline = avgSameDow.orElse(0.0) * coverageDays;

            // 9. Waste avoided quantity
            double wasteAvoidedQty = Math.max(0.0, baseline - optimalStock);

            // 10. Carbon accounting
            double kgPerUnit = item.getKgPerUnit() != null ? item.getKgPerUnit().doubleValue() : 0.0;
            double efProd = item.getEfProd() != null ? item.getEfProd().doubleValue() : 0.0;
            double efWaste = item.getEfWaste() != null ? item.getEfWaste().doubleValue() : 0.0;
            CarbonResult carbonResult = carbonAccountingService.compute(
                    wasteAvoidedQty, kgPerUnit, efProd, efWaste, item.isWasteTarget());

            // 11. Upsert OrderRecommendation — NEVER touch actualQuantity / actualUpdatedAt on update
            OrderRecommendation rec = orderRecommendationRepository
                    .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, targetDate)
                    .orElse(new OrderRecommendation());
            rec.setStoreId(storeId);
            rec.setItemId(itemId);
            rec.setTargetDate(targetDate);
            rec.setRecommendedQuantity(BigDecimal.valueOf(recommendedOrder));
            rec.setOptimalStockQuantity(BigDecimal.valueOf(optimalStock));
            rec.setBaselineQuantity(BigDecimal.valueOf(baseline));
            rec.setCriticalRatio(BigDecimal.valueOf(cr));
            rec.setExpectedWasteAvoidedKg(BigDecimal.valueOf(carbonResult.wasteAvoidedKg()));
            rec.setRationale("{\"computed\":true}");
            // actualQuantity and actualUpdatedAt are left untouched on existing records

            result.add(orderRecommendationRepository.save(rec));

            // 12. Upsert CarbonSaving
            CarbonSaving cs = carbonSavingRepository
                    .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, targetDate)
                    .orElse(new CarbonSaving());
            cs.setStoreId(storeId);
            cs.setItemId(itemId);
            cs.setTargetDate(targetDate);
            cs.setWasteAvoidedKg(BigDecimal.valueOf(carbonResult.wasteAvoidedKg()));
            cs.setGuaranteedSavingKg(BigDecimal.valueOf(carbonResult.guaranteedSavingKg()));
            cs.setPotentialSavingKg(BigDecimal.valueOf(carbonResult.potentialSavingKg()));
            cs.setEfProdSnapshot(BigDecimal.valueOf(efProd));
            cs.setEfWasteSnapshot(BigDecimal.valueOf(efWaste));
            carbonSavingRepository.save(cs);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse loadRecommendations(Long storeId, LocalDate date) {
        LocalDate seedDate = date.minusYears(1);
        List<OrderRecommendation> recs = orderRecommendationRepository.findByStoreIdAndTargetDate(storeId, seedDate);
        List<RecommendationItem> items = recs.stream().map(rec -> {
            ItemMaster item = itemMasterRepository.findById(rec.getItemId()).orElse(null);
            Optional<OrderPolicy> policy = orderPolicyRepository.findByStoreIdAndItemId(storeId, rec.getItemId());
            return new RecommendationItem(
                    rec.getItemId(),
                    item != null ? item.getName() : null,
                    rec.getRecommendedQuantity(),
                    rec.getActualQuantity(),
                    rec.getCriticalRatio(),
                    rec.getExpectedWasteAvoidedKg(),
                    policy.map(OrderPolicy::getOrderLotUnit).orElse(null));
        }).collect(Collectors.toList());
        return new RecommendationResponse(storeId, date, items);
    }

    @Transactional(readOnly = true)
    public CarbonTodayResponse getCarbonToday(Long storeId) {
        LocalDate today = LocalDate.now().minusYears(1);
        // Find the most recent targetDate
        List<CarbonSaving> recent = carbonSavingRepository
                .findByStoreIdAndTargetDateBetween(storeId, today.minusDays(90), today);

        if (recent.isEmpty()) {
            return new CarbonTodayResponse(today,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        LocalDate latestDate = recent.stream()
                .map(CarbonSaving::getTargetDate)
                .max(LocalDate::compareTo)
                .orElse(today);

        List<CarbonSaving> todaySavings = recent.stream()
                .filter(cs -> cs.getTargetDate().equals(latestDate))
                .collect(Collectors.toList());

        BigDecimal totalGuaranteed = todaySavings.stream()
                .map(CarbonSaving::getGuaranteedSavingKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPotential = todaySavings.stream()
                .map(CarbonSaving::getPotentialSavingKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal carKm = totalPotential.doubleValue() > 0
                ? BigDecimal.valueOf(totalPotential.doubleValue() / carKgco2PerKm)
                : BigDecimal.ZERO;

        // wasteCostAvoided: Σ(wasteAvoidedKg / kgPerUnit * purchasePrice) for items where kgPerUnit > 0
        BigDecimal wasteCostAvoided = BigDecimal.ZERO;
        for (CarbonSaving cs : todaySavings) {
            ItemMaster item = itemMasterRepository.findById(cs.getItemId()).orElse(null);
            if (item == null) continue;
            double kpu = item.getKgPerUnit() != null ? item.getKgPerUnit().doubleValue() : 0.0;
            double pp = item.getPurchasePrice() != null ? item.getPurchasePrice().doubleValue() : 0.0;
            double wak = cs.getWasteAvoidedKg() != null ? cs.getWasteAvoidedKg().doubleValue() : 0.0;
            if (kpu > 0) {
                wasteCostAvoided = wasteCostAvoided.add(BigDecimal.valueOf(wak / kpu * pp));
            }
        }

        List<CarbonItemDetail> byItem = todaySavings.stream()
                .map(cs -> new CarbonItemDetail(
                        cs.getItemId(),
                        cs.getGuaranteedSavingKg() != null ? cs.getGuaranteedSavingKg() : BigDecimal.ZERO,
                        cs.getPotentialSavingKg() != null ? cs.getPotentialSavingKg() : BigDecimal.ZERO))
                .collect(Collectors.toList());

        return new CarbonTodayResponse(latestDate, totalGuaranteed, totalPotential,
                carKm, wasteCostAvoided, byItem);
    }

    @Transactional(readOnly = true)
    public CarbonSavingsResponse getCarbonSavings(Long storeId, LocalDate from, LocalDate to) {
        List<CarbonSaving> savings = carbonSavingRepository.findByStoreIdAndTargetDateBetween(
                storeId, from.minusYears(1), to.minusYears(1));

        // Group by date, sum per date
        Map<LocalDate, BigDecimal[]> byDate = new TreeMap<>();
        for (CarbonSaving cs : savings) {
            BigDecimal[] acc = byDate.computeIfAbsent(cs.getTargetDate(), d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (cs.getGuaranteedSavingKg() != null) acc[0] = acc[0].add(cs.getGuaranteedSavingKg());
            if (cs.getPotentialSavingKg() != null) acc[1] = acc[1].add(cs.getPotentialSavingKg());
        }

        List<CarbonSeriesPoint> series = byDate.entrySet().stream()
                .map(e -> new CarbonSeriesPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .collect(Collectors.toList());

        return new CarbonSavingsResponse(series);
    }

    @Transactional(readOnly = true)
    public CarbonSummaryResponse getCarbonSummary(Long storeId) {
        List<CarbonSaving> all = carbonSavingRepository.findByStoreIdAndTargetDateBetween(
                storeId, LocalDate.of(2000, 1, 1), LocalDate.now().minusYears(1));

        BigDecimal totalGuaranteed = all.stream()
                .map(CarbonSaving::getGuaranteedSavingKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPotential = all.stream()
                .map(CarbonSaving::getPotentialSavingKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long periodDays = all.stream()
                .map(CarbonSaving::getTargetDate)
                .distinct()
                .count();
        BigDecimal carKm = totalPotential.doubleValue() > 0
                ? BigDecimal.valueOf(totalPotential.doubleValue() / carKgco2PerKm)
                : BigDecimal.ZERO;

        return new CarbonSummaryResponse(totalGuaranteed, totalPotential, carKm, periodDays);
    }
}
