package com.netzero.dashboard.controller;

import com.netzero.common.ApiResponse;
import com.netzero.dashboard.dto.DashboardSummary;
import com.netzero.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/v1/dashboard/summary?storeId={storeId}
     * Returns today's summary: latest recommendation date, due item count,
     * today's recommended orders, and today's carbon savings.
     */
    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary(@RequestParam Long storeId) {
        return ApiResponse.ok(dashboardService.summary(storeId));
    }
}
