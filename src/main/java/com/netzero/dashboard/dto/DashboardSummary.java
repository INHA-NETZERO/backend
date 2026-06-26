package com.netzero.dashboard.dto;

import com.netzero.order.dto.RecommendationItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for GET /api/v1/dashboard/summary.
 */
public record DashboardSummary(
    /** Latest target date for which order recommendations exist. */
    LocalDate latestTargetDate,
    /** Number of items due for ordering today (Asia/Seoul). */
    int dueItemCount,
    /** Today's order recommendations. */
    List<RecommendationItem> recommendedOrders,
    /** Total carbon savings (guaranteed + potential) for today (kg CO₂e). */
    BigDecimal carbonToday,
    /** WAPE (Weighted Absolute Percentage Error) by item name for today's actual vs forecast. */
    Map<String, Double> wapeByItem
) {}
