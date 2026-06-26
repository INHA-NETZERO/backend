package com.netzero.order.controller;

import com.netzero.common.ApiResponse;
import com.netzero.order.dto.ActualOrderRequest;
import com.netzero.order.dto.ActualOrderResult;
import com.netzero.order.dto.OrderHistoryResponse;
import com.netzero.order.dto.RecommendationResponse;
import com.netzero.order.service.ActualOrderService;
import com.netzero.order.service.OrderHistoryService;
import com.netzero.order.service.OrderOptimizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final OrderOptimizationService orderOptimizationService;
    private final ActualOrderService actualOrderService;
    private final OrderHistoryService orderHistoryService;

    public RecommendationController(OrderOptimizationService orderOptimizationService,
                                    ActualOrderService actualOrderService,
                                    OrderHistoryService orderHistoryService) {
        this.orderOptimizationService = orderOptimizationService;
        this.actualOrderService = actualOrderService;
        this.orderHistoryService = orderHistoryService;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(orderOptimizationService.loadRecommendations(storeId, date));
    }

    @GetMapping("/history")
    public ApiResponse<OrderHistoryResponse> getHistory(
            @RequestParam Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderHistoryService.history(storeId, page, size));
    }

    @PutMapping("/actual")
    public ApiResponse<ActualOrderResult> updateActual(@RequestBody ActualOrderRequest req) {
        return ApiResponse.ok(actualOrderService.apply(req));
    }
}
