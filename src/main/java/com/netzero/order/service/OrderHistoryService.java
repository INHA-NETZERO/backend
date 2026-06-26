package com.netzero.order.service;

import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.dto.OrderHistoryEntry;
import com.netzero.order.dto.OrderHistoryResponse;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.repository.ItemMasterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderHistoryService {

    private final OrderRecommendationRepository orderRecommendationRepository;
    private final ItemMasterRepository itemMasterRepository;

    public OrderHistoryService(OrderRecommendationRepository orderRecommendationRepository,
                               ItemMasterRepository itemMasterRepository) {
        this.orderRecommendationRepository = orderRecommendationRepository;
        this.itemMasterRepository = itemMasterRepository;
    }

    @Transactional(readOnly = true)
    public OrderHistoryResponse history(Long storeId, int page, int size) {
        Page<LocalDate> dates = orderRecommendationRepository
                .findConfirmedDatesByStoreId(storeId, PageRequest.of(page, size));

        Map<Long, ItemMaster> itemCache = itemMasterRepository.findAll()
                .stream().collect(Collectors.toMap(ItemMaster::getId, i -> i));

        List<OrderHistoryEntry> content = dates.getContent().stream()
                .map(date -> buildEntry(storeId, date, itemCache))
                .toList();

        return new OrderHistoryResponse(content, page, size, dates.getTotalElements(), dates.getTotalPages());
    }

    private OrderHistoryEntry buildEntry(Long storeId, LocalDate date, Map<Long, ItemMaster> itemCache) {
        List<OrderRecommendation> recs = orderRecommendationRepository
                .findAllByStoreIdAndTargetDate(storeId, date);

        int itemCount = recs.size();

        BigDecimal totalActualQty = recs.stream()
                .map(r -> r.getActualQuantity() != null ? r.getActualQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBaseline = recs.stream()
                .map(r -> r.getBaselineQuantity() != null ? r.getBaselineQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vsBaselineQty = totalActualQty.subtract(totalBaseline);

        // 절감 원가: (baseline - actual) × purchase_price per item
        BigDecimal estimatedCostSaving = recs.stream()
                .map(r -> {
                    BigDecimal actual   = r.getActualQuantity()   != null ? r.getActualQuantity()   : BigDecimal.ZERO;
                    BigDecimal baseline = r.getBaselineQuantity() != null ? r.getBaselineQuantity() : BigDecimal.ZERO;
                    BigDecimal diff = baseline.subtract(actual);
                    if (diff.signum() <= 0) return BigDecimal.ZERO;
                    ItemMaster item = itemCache.get(r.getItemId());
                    BigDecimal price = (item != null && item.getPurchasePrice() != null)
                            ? item.getPurchasePrice() : BigDecimal.ZERO;
                    return diff.multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 탄소 절감: expectedWasteAvoidedKg × efProd per item
        BigDecimal estimatedCarbonSaving = recs.stream()
                .map(r -> {
                    BigDecimal wasteAvoided = r.getExpectedWasteAvoidedKg() != null
                            ? r.getExpectedWasteAvoidedKg() : BigDecimal.ZERO;
                    ItemMaster item = itemCache.get(r.getItemId());
                    BigDecimal ef = (item != null && item.getEfProd() != null)
                            ? item.getEfProd() : BigDecimal.ZERO;
                    return wasteAvoided.multiply(ef);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderHistoryEntry(
                date, itemCount, totalActualQty, vsBaselineQty,
                estimatedCostSaving, estimatedCarbonSaving, "확정됨");
    }
}
