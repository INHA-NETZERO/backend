package com.netzero.order.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendationResponse(
        Long storeId,
        LocalDate date,
        List<RecommendationItem> items
) {}
