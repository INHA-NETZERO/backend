package com.netzero.order;

import com.netzero.config.SecurityConfig;
import com.netzero.order.controller.RecommendationController;
import com.netzero.order.dto.RecommendationItem;
import com.netzero.order.dto.RecommendationResponse;
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

@WebMvcTest(RecommendationController.class)
@Import(SecurityConfig.class)
class RecommendationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderOptimizationService orderOptimizationService;

    @Test
    void getRecommendations_returns200AndSuccessTrue() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 27);
        RecommendationItem item = new RecommendationItem(
                1L, "우유", BigDecimal.valueOf(58), null,
                BigDecimal.valueOf(0.23), BigDecimal.valueOf(5.5), BigDecimal.valueOf(2));
        RecommendationResponse response = new RecommendationResponse(1L, date, List.of(item));

        when(orderOptimizationService.loadRecommendations(anyLong(), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("storeId", "1")
                        .param("date", "2026-06-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storeId").value(1))
                .andExpect(jsonPath("$.data.items[0].itemName").value("우유"));
    }
}
