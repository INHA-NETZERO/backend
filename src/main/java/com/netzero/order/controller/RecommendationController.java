package com.netzero.order.controller;

import com.netzero.common.ApiResponse;
import com.netzero.order.dto.ActualOrderRequest;
import com.netzero.order.dto.ActualOrderResult;
import com.netzero.order.dto.RecommendationResponse;
import com.netzero.order.service.ActualOrderService;
import com.netzero.order.service.OrderOptimizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final OrderOptimizationService orderOptimizationService;
    private final ActualOrderService actualOrderService;

    public RecommendationController(OrderOptimizationService orderOptimizationService,
                                    ActualOrderService actualOrderService) {
        this.orderOptimizationService = orderOptimizationService;
        this.actualOrderService = actualOrderService;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(orderOptimizationService.loadRecommendations(storeId, date));
    }

    @PutMapping("/actual")
    public ApiResponse<ActualOrderResult> updateActual(@RequestBody ActualOrderRequest req) {
        return ApiResponse.ok(actualOrderService.apply(req));
    }
}
