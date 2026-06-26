package com.netzero.order.dto;

import java.time.LocalDate;
import java.util.List;

public record ActualOrderRequest(
        Long storeId,
        LocalDate targetDate,
        List<ActualOrderItem> items
) {}
