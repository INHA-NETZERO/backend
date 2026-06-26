package com.netzero.carbon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CarbonTodayResponse(
        LocalDate date,
        BigDecimal guaranteedSavingKg,
        BigDecimal potentialSavingKg,
        BigDecimal carEquivalentKm,
        BigDecimal wasteCostAvoidedKrw,
        List<CarbonItemDetail> byItem
) {}
