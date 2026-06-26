package com.netzero.order;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.forecast.service.DemandForecastService;
import com.netzero.order.dto.ActualOrderItem;
import com.netzero.order.dto.ActualOrderRequest;
import com.netzero.order.dto.ActualOrderResult;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.order.service.ActualOrderService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ActualOrderServiceTest {

    @Autowired
    ActualOrderService actualOrderService;

    @Autowired
    OrderOptimizationService optimizationService;

    @Autowired
    DemandForecastService forecastService;

    @Autowired
    ItemMasterRepository itemMasterRepository;

    @Autowired
    InventorySnapshotRepository inventorySnapshotRepository;

    @Autowired
    SalesRecordRepository salesRecordRepository;

    @Autowired
    OrderRecommendationRepository orderRecommendationRepository;

    private static final Long STORE_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 1);

    @Test
    void apply_updatesActualQuantityAndPreservesRecommended() {
        ItemMaster milk = itemMasterRepository.findByName("우유").orElseThrow();
        Long milkId = milk.getId();

        // Seed inventory and sales records so optimize() can produce a recommendation
        seedInventoryAndSales(milk, milkId);

        // Produce an OrderRecommendation via optimize
        forecastService.forecast(STORE_ID, TARGET_DATE, List.of(milkId), List.of(), List.of());
        var recs = optimizationService.optimize(STORE_ID, TARGET_DATE, List.of(milkId));
        assertThat(recs).hasSize(1);
        BigDecimal recommendedQty = recs.get(0).getRecommendedQuantity();

        // Apply actual quantity
        ActualOrderRequest req = new ActualOrderRequest(
                STORE_ID,
                TARGET_DATE,
                List.of(new ActualOrderItem(milkId, BigDecimal.valueOf(60))));

        ActualOrderResult result = actualOrderService.apply(req);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.notFound()).isEmpty();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).actualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(result.items().get(0).recommendedQuantity()).isEqualByComparingTo(recommendedQty);

        // Verify persisted entity
        var saved = orderRecommendationRepository
                .findByStoreIdAndItemIdAndTargetDate(STORE_ID, milkId, TARGET_DATE)
                .orElseThrow();
        assertThat(saved.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(saved.getActualUpdatedAt()).isNotNull();
        assertThat(saved.getRecommendedQuantity()).isEqualByComparingTo(recommendedQty);
    }

    @Test
    void apply_notFoundItemAddedToNotFoundList() {
        long nonExistentItemId = 999_999L;

        ActualOrderRequest req = new ActualOrderRequest(
                STORE_ID,
                TARGET_DATE,
                List.of(new ActualOrderItem(nonExistentItemId, BigDecimal.valueOf(10))));

        ActualOrderResult result = actualOrderService.apply(req);

        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.notFound()).containsExactly(nonExistentItemId);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void apply_negativeQuantityThrowsValidationError() {
        ItemMaster milk = itemMasterRepository.findByName("우유").orElseThrow();

        ActualOrderRequest req = new ActualOrderRequest(
                STORE_ID,
                TARGET_DATE,
                List.of(new ActualOrderItem(milk.getId(), BigDecimal.valueOf(-1))));

        assertThatThrownBy(() -> actualOrderService.apply(req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void optimize_rerun_doesNotOverwriteActualQuantity() {
        ItemMaster milk = itemMasterRepository.findByName("우유").orElseThrow();
        Long milkId = milk.getId();

        seedInventoryAndSales(milk, milkId);

        // First optimize
        forecastService.forecast(STORE_ID, TARGET_DATE, List.of(milkId), List.of(), List.of());
        optimizationService.optimize(STORE_ID, TARGET_DATE, List.of(milkId));

        // Set actual quantity
        ActualOrderRequest req = new ActualOrderRequest(
                STORE_ID,
                TARGET_DATE,
                List.of(new ActualOrderItem(milkId, BigDecimal.valueOf(55))));
        actualOrderService.apply(req);

        // Re-run optimize
        optimizationService.optimize(STORE_ID, TARGET_DATE, List.of(milkId));

        // Actual quantity must still be 55
        var saved = orderRecommendationRepository
                .findByStoreIdAndItemIdAndTargetDate(STORE_ID, milkId, TARGET_DATE)
                .orElseThrow();
        assertThat(saved.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(55));
    }

    // --- helpers ---

    private void seedInventoryAndSales(ItemMaster item, Long itemId) {
        InventorySnapshot inv = new InventorySnapshot();
        inv.setStoreId(STORE_ID);
        inv.setItem(item);
        inv.setBusinessDate(TARGET_DATE);
        inv.setClosingStock(BigDecimal.valueOf(12.0));
        inventorySnapshotRepository.save(inv);

        for (int i = 7; i >= 1; i--) {
            SalesRecord sr = new SalesRecord(
                    STORE_ID, TARGET_DATE.minusDays(i),
                    null, null, null, null, null, null, null,
                    itemId, BigDecimal.valueOf(10.0), null);
            salesRecordRepository.save(sr);
        }
    }
}
