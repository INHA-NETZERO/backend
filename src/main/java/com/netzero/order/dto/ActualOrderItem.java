package com.netzero.order.dto;

import java.math.BigDecimal;

public record ActualOrderItem(Long itemId, BigDecimal actualQuantity) {}
