package com.netzero.export;

import com.netzero.config.SecurityConfig;
import com.netzero.export.controller.ExportController;
import com.netzero.export.scheduler.MonthlyExportScheduler;
import com.netzero.export.service.InventoryFlowExporter;
import com.netzero.export.service.PresignService;
import com.netzero.export.service.SalesCsvExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@Import(SecurityConfig.class)
class ExportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SalesCsvExporter salesCsvExporter;

    @MockitoBean
    InventoryFlowExporter inventoryFlowExporter;

    @MockitoBean
    MonthlyExportScheduler monthlyExportScheduler;

    @MockitoBean
    PresignService presignService;

    @Test
    void salesCsvEndpoint_returns200WithCsvContentType() throws Exception {
        mvc.perform(get("/api/v1/export/sales.csv")
                .param("storeId", "1")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    @Test
    void inventoryCsvEndpoint_returns200WithCsvContentType() throws Exception {
        mvc.perform(get("/api/v1/export/store-inventory.csv")
                .param("storeId", "1")
                .param("date", "2026-06-27"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("text/csv")));
    }
}
