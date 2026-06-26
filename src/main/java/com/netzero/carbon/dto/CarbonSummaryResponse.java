package com.netzero.carbon.dto;

import java.math.BigDecimal;

public record CarbonSummaryResponse(
        BigDecimal totalGuaranteedKg,
        BigDecimal totalPotentialKg,
        BigDecimal carEquivalentKm,
        long periodDays
) {}
