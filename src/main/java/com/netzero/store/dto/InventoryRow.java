package com.netzero.store.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryRow(
        Long itemId,
        String itemName,
        String category,
        String unit,
        BigDecimal orderedQty,
        BigDecimal openingStock,
        BigDecimal demand,
        BigDecimal actualSales,
        BigDecimal stockout,
        BigDecimal wasteQty,
        BigDecimal closingStock,
        BigDecimal wasteKg,
        BigDecimal wasteCarbonKg,
        BigDecimal wasteCostKrw,
        LocalDate lastOrderDate
) {}
