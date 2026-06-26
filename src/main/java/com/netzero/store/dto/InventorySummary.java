package com.netzero.store.dto;

import java.math.BigDecimal;

public record InventorySummary(
        BigDecimal totalWasteKg,
        BigDecimal totalWasteCarbonKg,
        BigDecimal totalWasteCostKrw
) {}
