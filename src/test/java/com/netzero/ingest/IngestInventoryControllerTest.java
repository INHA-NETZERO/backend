package com.netzero.ingest;

import com.netzero.config.SecurityConfig;
import com.netzero.ingest.controller.IngestController;
import com.netzero.ingest.dto.IngestResult;
import com.netzero.ingest.service.InventoryCsvService;
import com.netzero.ingest.service.SalesCsvService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
@Import(SecurityConfig.class)
class IngestInventoryControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryCsvService inventoryCsvService;

    @MockitoBean
    SalesCsvService salesCsvService;

    @Test
    void inventoryUpload_returnsAccepted() throws Exception {
        given(inventoryCsvService.ingest(anyLong(), any()))
                .willReturn(new IngestResult(3, 0, List.of()));

        mvc.perform(multipart("/api/v1/ingest/inventory")
                        .file("file", "날짜,요일,품목,...\n2026-06-01,월,우유,...\n".getBytes())
                        .param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(3));
    }
}
