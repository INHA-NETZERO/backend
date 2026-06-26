package com.netzero.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderHistoryEntry(
        LocalDate targetDate,
        int itemCount,
        BigDecimal totalActualQty,
        BigDecimal vsBaselineQty,
        BigDecimal estimatedCostSavingKrw,
        BigDecimal estimatedCarbonSavingKg,
        String status
) {}
