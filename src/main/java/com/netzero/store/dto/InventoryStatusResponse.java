package com.netzero.store.dto;

import java.time.LocalDate;
import java.util.List;

public record InventoryStatusResponse(
        Long storeId,
        LocalDate businessDate,
        String dayOfWeek,
        int itemCount,
        InventorySummary summary,
        List<InventoryRow> items
) {}
