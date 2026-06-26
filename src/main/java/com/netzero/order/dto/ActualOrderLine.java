package com.netzero.order.dto;

import java.math.BigDecimal;

public record ActualOrderLine(
        Long itemId,
        String itemName,
        BigDecimal recommendedQuantity,
        BigDecimal actualQuantity
) {}
