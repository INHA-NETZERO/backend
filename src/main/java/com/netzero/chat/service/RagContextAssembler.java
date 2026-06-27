package com.netzero.chat.service;

import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.chat.port.Grounding;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG(Retrieval-Augmented Generation) 컨텍스트 조립 서비스.
 * 주어진 storeId / date / itemId 에 대해 DB에서 데이터를 조회하여
 * LLM 프롬프트에 전달할 Grounding 맵을 구성한다.
 */
@Service
public class RagContextAssembler {

    private final OrderRecommendationRepository orderRecommendationRepo;
    private final CarbonSavingRepository carbonSavingRepo;
    private final DemandForecastRepository demandForecastRepo;
    private final ItemMasterRepository itemMasterRepo;
    private final OrderPolicyRepository orderPolicyRepo;
    private final InventorySnapshotRepository inventorySnapshotRepo;

    private static final double CAR_KGCO2_PER_KM = 4.6;

    public RagContextAssembler(
            OrderRecommendationRepository orderRecommendationRepo,
            CarbonSavingRepository carbonSavingRepo,
            DemandForecastRepository demandForecastRepo,
            ItemMasterRepository itemMasterRepo,
            OrderPolicyRepository orderPolicyRepo,
            InventorySnapshotRepository inventorySnapshotRepo) {
        this.orderRecommendationRepo = orderRecommendationRepo;
        this.carbonSavingRepo = carbonSavingRepo;
        this.demandForecastRepo = demandForecastRepo;
        this.itemMasterRepo = itemMasterRepo;
        this.orderPolicyRepo = orderPolicyRepo;
        this.inventorySnapshotRepo = inventorySnapshotRepo;
    }

    public Grounding assemble(Long storeId, LocalDate date, Long itemId) {
        LocalDate seedDate = date.minusYears(1);

        Map<String, Object> item = itemMasterRepo.findById(itemId)
                .map(im -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("itemId", itemId);
                    m.put("itemName", im.getName());
                    m.put("unit", im.getUnit());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> forecast = demandForecastRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, seedDate)
                .map(df -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("p10", df.getP10());
                    m.put("p50", df.getP50());
                    m.put("p90", df.getP90());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> recommendation = orderRecommendationRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, seedDate)
                .map(or -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("recommendedQuantity", or.getRecommendedQuantity());
                    m.put("optimalStockQuantity", or.getOptimalStockQuantity());
                    m.put("baselineQuantity", or.getBaselineQuantity());
                    m.put("criticalRatio", or.getCriticalRatio());
                    double onHand = inventorySnapshotRepo
                            .findByStoreIdAndItem_IdAndBusinessDate(storeId, itemId, seedDate)
                            .map(inv -> inv.getClosingStock() != null ? inv.getClosingStock().doubleValue() : 0.0)
                            .orElse(0.0);
                    m.put("onHand", onHand);
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> carbon = carbonSavingRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, seedDate)
                .map(cs -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("wasteAvoidedKg", cs.getWasteAvoidedKg());
                    m.put("guaranteedSavingKg", cs.getGuaranteedSavingKg());
                    m.put("potentialSavingKg", cs.getPotentialSavingKg());
                    double potential = cs.getPotentialSavingKg() != null ? cs.getPotentialSavingKg().doubleValue() : 0.0;
                    m.put("carEquivalentKm", potential > 0 ? Math.round(potential / CAR_KGCO2_PER_KM * 10.0) / 10.0 : 0.0);
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> coverage = orderPolicyRepo
                .findByStoreIdAndItemId(storeId, itemId)
                .map(op -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("leadTimeDays", op.getLeadTimeDays());
                    m.put("orderCycleDays", op.getOrderCycleDays());
                    m.put("coverageDays", op.getLeadTimeDays() + op.getOrderCycleDays());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> context = new HashMap<>();
        context.put("date", date.toString());
        context.put("storeId", storeId);

        return new Grounding(item, coverage, forecast, recommendation, carbon, context);
    }
}
