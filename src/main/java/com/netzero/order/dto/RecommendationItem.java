package com.netzero.order.dto;

import java.math.BigDecimal;

public record RecommendationItem(
        Long itemId,
        String itemName,
        BigDecimal recommendedQuantity,
        BigDecimal actualQuantity,
        BigDecimal criticalRatio,
        BigDecimal expectedWasteAvoidedKg,
        BigDecimal orderLotUnit
) {}
