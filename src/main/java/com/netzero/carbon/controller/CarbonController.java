package com.netzero.carbon.controller;

import com.netzero.carbon.dto.CarbonSavingsResponse;
import com.netzero.carbon.dto.CarbonSummaryResponse;
import com.netzero.carbon.dto.CarbonTodayResponse;
import com.netzero.common.ApiResponse;
import com.netzero.order.service.OrderOptimizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/carbon")
public class CarbonController {

    private final OrderOptimizationService orderOptimizationService;

    public CarbonController(OrderOptimizationService orderOptimizationService) {
        this.orderOptimizationService = orderOptimizationService;
    }

    @GetMapping("/today")
    public ApiResponse<CarbonTodayResponse> getToday(@RequestParam Long storeId) {
        return ApiResponse.ok(orderOptimizationService.getCarbonToday(storeId));
    }

    @GetMapping("/savings")
    public ApiResponse<CarbonSavingsResponse> getSavings(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(orderOptimizationService.getCarbonSavings(storeId, from, to));
    }

    @GetMapping("/savings/summary")
    public ApiResponse<CarbonSummaryResponse> getSummary(@RequestParam Long storeId) {
        return ApiResponse.ok(orderOptimizationService.getCarbonSummary(storeId));
    }
}
