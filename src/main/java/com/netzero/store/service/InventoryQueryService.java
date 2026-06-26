package com.netzero.store.service;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.store.domain.InventorySnapshot;
import com.netzero.store.dto.InventoryRow;
import com.netzero.store.dto.InventoryStatusResponse;
import com.netzero.store.dto.InventorySummary;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class InventoryQueryService {

    private final InventorySnapshotRepository repo;
    private final StoreRepository stores;

    public InventoryQueryService(InventorySnapshotRepository repo, StoreRepository stores) {
        this.repo = repo;
        this.stores = stores;
    }

    public InventoryStatusResponse statusOn(Long storeId, LocalDate date,
            String category, Boolean wasteTargetOnly) {
        if (!stores.existsById(storeId)) {
            throw new ApiException(ErrorCode.STORE_NOT_FOUND, "Store not found: " + storeId);
        }

        List<InventorySnapshot> snapshots = repo.findByStoreIdAndBusinessDate(storeId, date);

        Stream<InventorySnapshot> stream = snapshots.stream();
        if (Boolean.TRUE.equals(wasteTargetOnly)) {
            stream = stream.filter(s -> s.getItem().isWasteTarget());
        } else if (category != null && !category.isBlank()) {
            stream = stream.filter(s -> category.equals(s.getCategory()));
        }

        List<InventorySnapshot> filtered = stream
                .sorted(Comparator.comparing(InventorySnapshot::getCategory,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(s -> s.getItem().getName(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<InventoryRow> rows = filtered.stream().map(s -> new InventoryRow(
                s.getItem().getId(),
                s.getItem().getName(),
                s.getCategory(),
                s.getUnit(),
                s.getOrderedQty(),
                s.getOpeningStock(),
                s.getDemand(),
                s.getActualSales(),
                s.getStockout(),
                s.getWasteQty(),
                s.getClosingStock(),
                s.getWasteKg(),
                s.getWasteCarbonKg(),
                s.getWasteCostKrw(),
                s.getLastOrderDate()
        )).toList();

        BigDecimal totalWasteKg = filtered.stream()
                .map(s -> s.getWasteKg() != null ? s.getWasteKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWasteCarbonKg = filtered.stream()
                .map(s -> s.getWasteCarbonKg() != null ? s.getWasteCarbonKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWasteCostKrw = filtered.stream()
                .map(s -> s.getWasteCostKrw() != null ? s.getWasteCostKrw() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        InventorySummary summary = new InventorySummary(totalWasteKg, totalWasteCarbonKg, totalWasteCostKrw);

        String dayOfWeek = filtered.isEmpty() ? null : filtered.get(0).getDayOfWeek();
        return new InventoryStatusResponse(storeId, date, dayOfWeek, rows.size(), summary, rows);
    }
}
