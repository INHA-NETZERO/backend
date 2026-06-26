package com.netzero.chat.service;

import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.chat.port.Grounding;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.order.repository.OrderRecommendationRepository;
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

    public RagContextAssembler(
            OrderRecommendationRepository orderRecommendationRepo,
            CarbonSavingRepository carbonSavingRepo,
            DemandForecastRepository demandForecastRepo,
            ItemMasterRepository itemMasterRepo,
            OrderPolicyRepository orderPolicyRepo) {
        this.orderRecommendationRepo = orderRecommendationRepo;
        this.carbonSavingRepo = carbonSavingRepo;
        this.demandForecastRepo = demandForecastRepo;
        this.itemMasterRepo = itemMasterRepo;
        this.orderPolicyRepo = orderPolicyRepo;
    }

    public Grounding assemble(Long storeId, LocalDate date, Long itemId) {
        Map<String, Object> item = itemMasterRepo.findById(itemId)
                .map(im -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", im.getName());
                    m.put("category", im.getCategory() != null ? im.getCategory().name() : null);
                    m.put("kgPerUnit", im.getKgPerUnit());
                    m.put("efProd", im.getEfProd());
                    m.put("efWaste", im.getEfWaste());
                    m.put("purchasePrice", im.getPurchasePrice());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> forecast = demandForecastRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, date)
                .map(df -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("p10", df.getP10());
                    m.put("p50", df.getP50());
                    m.put("p90", df.getP90());
                    m.put("modelVersion", df.getModelVersion());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> recommendation = orderRecommendationRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, date)
                .map(or -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("recommendedQuantity", or.getRecommendedQuantity());
                    m.put("criticalRatio", or.getCriticalRatio());
                    m.put("wasteAvoidedQty", or.getExpectedWasteAvoidedKg());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> carbon = carbonSavingRepo
                .findByStoreIdAndItemIdAndTargetDate(storeId, itemId, date)
                .map(cs -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("guaranteedSavingKg", cs.getGuaranteedSavingKg());
                    m.put("potentialSavingKg", cs.getPotentialSavingKg());
                    m.put("wasteAvoidedKg", cs.getWasteAvoidedKg());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> coverage = orderPolicyRepo
                .findByStoreIdAndItemId(storeId, itemId)
                .map(op -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("orderCycleDays", op.getOrderCycleDays());
                    m.put("leadTimeDays", op.getLeadTimeDays());
                    return (Map<String, Object>) m;
                })
                .orElse(Map.of());

        Map<String, Object> context = new HashMap<>();
        context.put("date", date.toString());
        context.put("storeId", storeId);
        if (!forecast.isEmpty()) {
            context.put("modelVersion", forecast.get("modelVersion"));
        }

        return new Grounding(item, coverage, forecast, recommendation, carbon, context);
    }
}
