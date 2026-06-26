package com.netzero.carbon;

import com.netzero.carbon.controller.CarbonController;
import com.netzero.carbon.dto.*;
import com.netzero.config.SecurityConfig;
import com.netzero.order.service.OrderOptimizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarbonController.class)
@Import(SecurityConfig.class)
class CarbonControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderOptimizationService orderOptimizationService;

    @Test
    void today_returns200AndSuccessTrue() throws Exception {
        CarbonTodayResponse response = new CarbonTodayResponse(
                LocalDate.of(2026, 6, 27),
                BigDecimal.valueOf(2.5),
                BigDecimal.valueOf(8.0),
                BigDecimal.valueOf(1.7),
                BigDecimal.valueOf(5000),
                List.of(new CarbonItemDetail(1L, BigDecimal.valueOf(2.5), BigDecimal.valueOf(8.0))));

        when(orderOptimizationService.getCarbonToday(anyLong())).thenReturn(response);

        mockMvc.perform(get("/api/v1/carbon/today").param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.date").value("2026-06-27"));
    }

    @Test
    void savings_returns200AndSuccessTrue() throws Exception {
        CarbonSavingsResponse response = new CarbonSavingsResponse(
                List.of(new CarbonSeriesPoint(LocalDate.of(2026, 6, 27),
                        BigDecimal.valueOf(2.5), BigDecimal.valueOf(8.0))));

        when(orderOptimizationService.getCarbonSavings(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/carbon/savings")
                        .param("storeId", "1")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.series").isArray());
    }

    @Test
    void summary_returns200AndSuccessTrue() throws Exception {
        CarbonSummaryResponse response = new CarbonSummaryResponse(
                BigDecimal.valueOf(10.0), BigDecimal.valueOf(30.0), BigDecimal.valueOf(6.5), 7L);

        when(orderOptimizationService.getCarbonSummary(anyLong())).thenReturn(response);

        mockMvc.perform(get("/api/v1/carbon/savings/summary").param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalGuaranteedKg").value(10.0));
    }
}
