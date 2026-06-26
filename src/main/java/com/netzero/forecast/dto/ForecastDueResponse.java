package com.netzero.forecast.dto;

import com.netzero.order.service.DueItemSelector;

import java.util.List;

/**
 * Response DTO for GET /api/v1/forecast — returns items due (and skipped) for ordering.
 */
public record ForecastDueResponse(
    List<DueItemSelector.DueItem> dueItems,
    List<DueItemSelector.SkippedItem> skipped
) {}
