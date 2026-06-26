package com.netzero.carbon.dto;

import java.math.BigDecimal;

public record CarbonItemDetail(
        Long itemId,
        BigDecimal guaranteedSavingKg,
        BigDecimal potentialSavingKg
) {}
