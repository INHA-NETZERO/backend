package com.netzero.order;

import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.forecast.service.DemandForecastService;
import com.netzero.order.service.OrderOptimizationService;
import com.netzero.store.domain.InventorySnapshot;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.domain.SalesRecord;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.SalesRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderOptimizationServiceTest {

    @Autowired
    DemandForecastService forecastService;

    @Autowired
    OrderOptimizationService svc;

    @Autowired
    ItemMasterRepository items;

    @Autowired
    CarbonSavingRepository carbon;

    @Autowired
    InventorySnapshotRepository inventorySnapshotRepository;

    @Autowired
    SalesRecordRepository salesRecordRepository;

    @Test
    void producesRecommendationAndCarbon() {
        ItemMaster milk = items.findByName("우유").orElseThrow();
        Long milkId = milk.getId();
        LocalDate date = LocalDate.parse("2026-06-27");

        // Given: inventory snapshot with closingStock=12 on target date
        insertInventorySnapshot(1L, milk, date, BigDecimal.valueOf(12.0));

        // Given: 7 days of sales records for ma7 > 0
        for (int i = 7; i >= 1; i--) {
            insertSalesRecord(1L, milkId, date.minusDays(i), BigDecimal.valueOf(10.0));
        }

        // When
        forecastService.forecast(1L, date, List.of(milkId), List.of(), List.of());
        var recs = svc.optimize(1L, date, List.of(milkId));

        // Then
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).getRecommendedQuantity().doubleValue() % 2).isZero();
        assertThat(recs.get(0).getCriticalRatio().doubleValue()).isBetween(0.0, 1.0);
        assertThat(carbon.findByStoreIdAndItemIdAndTargetDate(1L, milkId, date)).isPresent();
    }

    private void insertInventorySnapshot(Long storeId, ItemMaster item, LocalDate date, BigDecimal closingStock) {
        InventorySnapshot inv = new InventorySnapshot();
        inv.setStoreId(storeId);
        inv.setItem(item);
        inv.setBusinessDate(date);
        inv.setClosingStock(closingStock);
        inventorySnapshotRepository.save(inv);
    }

    private void insertSalesRecord(Long storeId, Long itemId, LocalDate date, BigDecimal qty) {
        SalesRecord sr = new SalesRecord(
                storeId, date, null, null, null, null, null, null, null, itemId, qty, null);
        salesRecordRepository.save(sr);
    }
}
