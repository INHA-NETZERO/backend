package com.netzero.forecast.controller;

import com.netzero.common.ApiResponse;
import com.netzero.forecast.dto.ForecastDueResponse;
import com.netzero.order.service.DueItemSelector;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.OrderPolicyRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * GET /api/v1/forecast
 *
 * Returns the due-item selection for a given store and date (spec §3.2).
 * Useful for previewing which items will be ordered before triggering the pipeline.
 */
@RestController
@RequestMapping("/api/v1/forecast")
public class ForecastController {

    private final DueItemSelector dueItemSelector;

    public ForecastController(OrderPolicyRepository orderPolicyRepository,
                              InventorySnapshotRepository inventorySnapshotRepository) {
        this.dueItemSelector = new DueItemSelector(orderPolicyRepository, inventorySnapshotRepository);
    }

    @GetMapping
    public ApiResponse<ForecastDueResponse> getDueItems(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DueItemSelector.DueSelection selection = dueItemSelector.select(storeId, date);
        return ApiResponse.ok(new ForecastDueResponse(selection.due(), selection.skipped()));
    }
}
