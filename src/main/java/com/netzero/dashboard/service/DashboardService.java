package com.netzero.dashboard.service;

import com.netzero.carbon.domain.CarbonSaving;
import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.dashboard.dto.DashboardSummary;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.dto.RecommendationItem;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.order.service.DueItemSelector;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    public DashboardService(
            OrderPolicyRepository orderPolicyRepository,
            InventorySnapshotRepository inventorySnapshotRepository,
            OrderRecommendationRepository orderRecommendationRepository,
            CarbonSavingRepository carbonSavingRepository,
            ItemMasterRepository itemMasterRepository) {

        this.dueItemSelector = new DueItemSelector(orderPolicyRepository, inventorySnapshotRepository);
        this.orderRecommendationRepository = orderRecommendationRepository;
        this.carbonSavingRepository = carbonSavingRepository;
        this.itemMasterRepository = itemMasterRepository;
        this.orderPolicyRepository = orderPolicyRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long storeId) {
        LocalDate today = LocalDate.now(KST);

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

        return new DashboardSummary(latestTargetDate, dueItemCount, recommendedOrders, carbonToday);
    }
}
