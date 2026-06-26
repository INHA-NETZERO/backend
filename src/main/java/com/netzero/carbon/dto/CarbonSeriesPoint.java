package com.netzero.carbon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarbonSeriesPoint(
        LocalDate date,
        BigDecimal guaranteedSavingKg,
        BigDecimal potentialSavingKg
) {}
