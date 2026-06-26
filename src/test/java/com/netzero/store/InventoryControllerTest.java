package com.netzero.store;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.config.SecurityConfig;
import com.netzero.store.controller.InventoryController;
import com.netzero.store.dto.InventoryRow;
import com.netzero.store.dto.InventoryStatusResponse;
import com.netzero.store.dto.InventorySummary;
import com.netzero.store.service.InventoryQueryService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InventoryQueryService inventoryQueryService;

    @Test
    void status_validDateWithSnapshots_returns200WithItems() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 27);

        InventoryRow row = new InventoryRow(
                1L, "우유", "유제품", "개",
                BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(8),
                BigDecimal.valueOf(7), BigDecimal.ZERO, BigDecimal.valueOf(1),
                BigDecimal.valueOf(2), new BigDecimal("1.03"), new BigDecimal("2.06"),
                BigDecimal.valueOf(500), null
        );
        InventorySummary summary = new InventorySummary(
                new BigDecimal("1.03"), new BigDecimal("2.06"), BigDecimal.valueOf(500)
        );
        InventoryStatusResponse response = new InventoryStatusResponse(
                1L, date, "금", 1, summary, List.of(row)
        );

        when(inventoryQueryService.statusOn(anyLong(), any(LocalDate.class), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "1")
                        .param("date", "2026-06-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(1))
                .andExpect(jsonPath("$.data.items[0].itemName").value("우유"))
                .andExpect(jsonPath("$.data.summary.totalWasteKg").value(1.03));
    }

    @Test
    void status_dateWithNoSnapshots_returns200WithEmptyItems() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 1);

        InventorySummary summary = new InventorySummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        InventoryStatusResponse response = new InventoryStatusResponse(
                1L, date, null, 0, summary, List.of()
        );

        when(inventoryQueryService.statusOn(anyLong(), any(LocalDate.class), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "1")
                        .param("date", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void status_invalidDateFormat_returns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")
                        .param("storeId", "1")
                        .param("date", "20260627"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
