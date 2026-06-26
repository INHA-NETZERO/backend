package com.netzero.order;

import com.netzero.order.service.DueItemSelector;
import com.netzero.store.domain.InventorySnapshot;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DueItemSelectorTest {

    @Mock
    private OrderPolicyRepository orderPolicyRepository;

    @Mock
    private InventorySnapshotRepository inventorySnapshotRepository;

    private DueItemSelector selector;

    @BeforeEach
    void setUp() {
        selector = new DueItemSelector(orderPolicyRepository, inventorySnapshotRepository);
    }

    @Test
    void splitsDueAndSkipped() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");
        LocalDate milkLastOrder = currentDate.minusDays(8);   // 8 days ago
        LocalDate beansLastOrder = currentDate.minusDays(3);  // 3 days ago

        // Create mock OrderPolicies
        OrderPolicy milkPolicy = mock(OrderPolicy.class);
        when(milkPolicy.getItemId()).thenReturn(1L);
        when(milkPolicy.getItemName()).thenReturn("우유");
        when(milkPolicy.getOrderCycleDays()).thenReturn(7);
        when(milkPolicy.getLeadTimeDays()).thenReturn(1);

        OrderPolicy beansPolicy = mock(OrderPolicy.class);
        when(beansPolicy.getItemId()).thenReturn(2L);
        when(beansPolicy.getItemName()).thenReturn("원두");
        when(beansPolicy.getOrderCycleDays()).thenReturn(14);
        when(beansPolicy.getLeadTimeDays()).thenReturn(2);

        // Create mock InventorySnapshots
        InventorySnapshot milkSnapshot = mock(InventorySnapshot.class);
        when(milkSnapshot.getLastOrderDate()).thenReturn(milkLastOrder);

        InventorySnapshot beansSnapshot = mock(InventorySnapshot.class);
        when(beansSnapshot.getLastOrderDate()).thenReturn(beansLastOrder);

        // Set up repository mocks
        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of(milkPolicy, beansPolicy));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 1L, currentDate))
            .thenReturn(Optional.of(milkSnapshot));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 2L, currentDate))
            .thenReturn(Optional.of(beansSnapshot));

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due())
            .extracting(DueItemSelector.DueItem::itemId)
            .contains(1L)
            .doesNotContain(2L);

        assertThat(sel.skipped())
            .extracting(DueItemSelector.SkippedItem::itemId)
            .contains(2L);

        // Verify milk is due
        var milkDue = sel.due().stream()
            .filter(item -> item.itemId().equals(1L))
            .findFirst();
        assertThat(milkDue).isPresent();
        assertThat(milkDue.get().daysSinceLastOrder()).isEqualTo(8);
        assertThat(milkDue.get().orderCycleDays()).isEqualTo(7);

        // Verify beans is skipped with correct reason
        var beansSkipped = sel.skipped().stream()
            .filter(item -> item.itemId().equals(2L))
            .findFirst();
        assertThat(beansSkipped).isPresent();
        assertThat(beansSkipped.get().reason()).isEqualTo("NOT_DUE");
        assertThat(beansSkipped.get().daysSinceLastOrder()).isEqualTo(3);
        assertThat(beansSkipped.get().orderCycleDays()).isEqualTo(14);
    }

    @Test
    void itemWithNullLastOrderDateIsDue() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");

        OrderPolicy policy = mock(OrderPolicy.class);
        when(policy.getItemId()).thenReturn(1L);
        when(policy.getItemName()).thenReturn("새 상품");
        when(policy.getOrderCycleDays()).thenReturn(7);
        when(policy.getLeadTimeDays()).thenReturn(1);

        InventorySnapshot snapshot = mock(InventorySnapshot.class);
        when(snapshot.getLastOrderDate()).thenReturn(null);

        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of(policy));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 1L, currentDate))
            .thenReturn(Optional.of(snapshot));

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due())
            .extracting(DueItemSelector.DueItem::itemId)
            .contains(1L);
        assertThat(sel.skipped()).isEmpty();
    }

    @Test
    void itemWithoutSnapshotIsDue() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");

        OrderPolicy policy = mock(OrderPolicy.class);
        when(policy.getItemId()).thenReturn(1L);
        when(policy.getItemName()).thenReturn("상품");
        when(policy.getOrderCycleDays()).thenReturn(7);
        when(policy.getLeadTimeDays()).thenReturn(1);

        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of(policy));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 1L, currentDate))
            .thenReturn(Optional.empty());

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due())
            .extracting(DueItemSelector.DueItem::itemId)
            .contains(1L);
        assertThat(sel.skipped()).isEmpty();
    }

    @Test
    void itemExactlyAtOrderCycleBoundaryIsDue() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");
        LocalDate lastOrderDate = currentDate.minusDays(7);  // exactly 7 days ago

        OrderPolicy policy = mock(OrderPolicy.class);
        when(policy.getItemId()).thenReturn(1L);
        when(policy.getItemName()).thenReturn("상품");
        when(policy.getOrderCycleDays()).thenReturn(7);
        when(policy.getLeadTimeDays()).thenReturn(1);

        InventorySnapshot snapshot = mock(InventorySnapshot.class);
        when(snapshot.getLastOrderDate()).thenReturn(lastOrderDate);

        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of(policy));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 1L, currentDate))
            .thenReturn(Optional.of(snapshot));

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due())
            .extracting(DueItemSelector.DueItem::itemId)
            .contains(1L);
        assertThat(sel.skipped()).isEmpty();
    }

    @Test
    void itemJustBeforeOrderCycleIsSkipped() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");
        LocalDate lastOrderDate = currentDate.minusDays(6);  // 6 days ago, less than 7

        OrderPolicy policy = mock(OrderPolicy.class);
        when(policy.getItemId()).thenReturn(1L);
        when(policy.getItemName()).thenReturn("상품");
        when(policy.getOrderCycleDays()).thenReturn(7);
        when(policy.getLeadTimeDays()).thenReturn(1);

        InventorySnapshot snapshot = mock(InventorySnapshot.class);
        when(snapshot.getLastOrderDate()).thenReturn(lastOrderDate);

        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of(policy));

        when(inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, 1L, currentDate))
            .thenReturn(Optional.of(snapshot));

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due()).isEmpty();
        assertThat(sel.skipped())
            .extracting(DueItemSelector.SkippedItem::itemId)
            .contains(1L);
    }

    @Test
    void emptyStoreReturnsEmptySelection() {
        // given
        Long storeId = 1L;
        LocalDate currentDate = LocalDate.parse("2026-06-27");

        when(orderPolicyRepository.findByStoreId(storeId))
            .thenReturn(List.of());

        // when
        var sel = selector.select(storeId, currentDate);

        // then
        assertThat(sel.due()).isEmpty();
        assertThat(sel.skipped()).isEmpty();
    }
}
