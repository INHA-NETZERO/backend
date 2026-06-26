package com.netzero.order.dto;

import java.time.LocalDate;
import java.util.List;

public record ActualOrderResult(
        Long storeId,
        LocalDate targetDate,
        int updated,
        List<Long> notFound,
        List<ActualOrderLine> items
) {}
