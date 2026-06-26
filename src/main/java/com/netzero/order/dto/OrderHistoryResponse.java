package com.netzero.order.dto;

import java.util.List;

public record OrderHistoryResponse(
        List<OrderHistoryEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
