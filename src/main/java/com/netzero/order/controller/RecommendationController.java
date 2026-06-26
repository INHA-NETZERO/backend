package com.netzero.order.controller;

import com.netzero.common.ApiResponse;
import com.netzero.order.dto.RecommendationResponse;
import com.netzero.order.service.OrderOptimizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final OrderOptimizationService orderOptimizationService;

    public RecommendationController(OrderOptimizationService orderOptimizationService) {
        this.orderOptimizationService = orderOptimizationService;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(orderOptimizationService.loadRecommendations(storeId, date));
    }
}
