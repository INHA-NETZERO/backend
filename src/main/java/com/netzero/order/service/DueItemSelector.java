package com.netzero.order.service;

import com.netzero.store.domain.InventorySnapshot;
import com.netzero.store.domain.OrderPolicy;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DueItemSelector {

    private final OrderPolicyRepository orderPolicyRepository;
    private final InventorySnapshotRepository inventorySnapshotRepository;

    public DueItemSelector(OrderPolicyRepository orderPolicyRepository,
                           InventorySnapshotRepository inventorySnapshotRepository) {
        this.orderPolicyRepository = orderPolicyRepository;
        this.inventorySnapshotRepository = inventorySnapshotRepository;
    }

    /**
     * Selects items that are due for ordering.
     *
     * An item is considered due if:
     * - lastOrderDate is null, OR
     * - (date - lastOrderDate) >= orderCycleDays
     *
     * Items without an OrderPolicy are excluded entirely.
     */
    public DueSelection select(Long storeId, LocalDate date) {
        List<OrderPolicy> policies = orderPolicyRepository.findByStoreId(storeId);
        List<DueItem> due = new ArrayList<>();
        List<SkippedItem> skipped = new ArrayList<>();

        for (OrderPolicy policy : policies) {
            Long itemId = policy.getItemId();
            String itemName = policy.getItemName();
            int orderCycleDays = policy.getOrderCycleDays();
            int leadTimeDays = policy.getLeadTimeDays();

            // Get the latest InventorySnapshot for this item on the given date
            Optional<InventorySnapshot> snapshot =
                inventorySnapshotRepository.findByStoreIdAndItem_IdAndBusinessDate(storeId, itemId, date);

            LocalDate lastOrderDate = snapshot.map(InventorySnapshot::getLastOrderDate).orElse(null);

            long daysSinceLastOrder = calculateDaysSinceLastOrder(lastOrderDate, date);

            if (isDue(lastOrderDate, date, orderCycleDays)) {
                due.add(new DueItem(itemId, itemName, orderCycleDays, leadTimeDays, lastOrderDate, daysSinceLastOrder));
            } else {
                skipped.add(new SkippedItem(itemId, itemName, "NOT_DUE", daysSinceLastOrder, orderCycleDays));
            }
        }

        return new DueSelection(due, skipped);
    }

    private boolean isDue(LocalDate lastOrderDate, LocalDate currentDate, int orderCycleDays) {
        if (lastOrderDate == null) {
            return true;
        }
        long daysSince = ChronoUnit.DAYS.between(lastOrderDate, currentDate);
        return daysSince >= orderCycleDays;
    }

    private long calculateDaysSinceLastOrder(LocalDate lastOrderDate, LocalDate currentDate) {
        if (lastOrderDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(lastOrderDate, currentDate);
    }

    /**
     * Record representing an item that is due for ordering.
     */
    public record DueItem(
        Long itemId,
        String itemName,
        int orderCycleDays,
        int leadTimeDays,
        LocalDate lastOrderDate,
        long daysSinceLastOrder
    ) {}

    /**
     * Record representing an item that was skipped in this cycle.
     */
    public record SkippedItem(
        Long itemId,
        String itemName,
        String reason,
        long daysSinceLastOrder,
        int orderCycleDays
    ) {}

    /**
     * Record representing the result of a due item selection.
     */
    public record DueSelection(
        List<DueItem> due,
        List<SkippedItem> skipped
    ) {}
}
