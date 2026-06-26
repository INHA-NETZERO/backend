package com.netzero.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "security.api-key=test-secret")
class DemoSmokeTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${security.api-key:dev-demo-key}")
    String apiKey;

    @Test
    void fullDemoFlow() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        // 1. Ingest daily sales CSV
        String csv = "날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오\n"
            + today + ",금,우유,원재료,10,,,\n";
        mockMvc.perform(multipart("/api/v1/ingest/sales/daily")
                .file("file", csv.getBytes(StandardCharsets.UTF_8))
                .param("storeId", "1")
                .header("X-API-Key", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 2. Run pipeline
        mockMvc.perform(post("/api/v1/pipeline/run")
                .param("storeId", "1")
                .param("date", today.toString())
                .header("X-API-Key", apiKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 3. Get recommendations (may be empty if no due items, but should 200)
        mockMvc.perform(get("/api/v1/recommendations")
                .param("storeId", "1")
                .param("date", today.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 4. Get carbon today
        mockMvc.perform(get("/api/v1/carbon/today")
                .param("storeId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void writeEndpointRequiresApiKey() throws Exception {
        String csv = "날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오\n";
        mockMvc.perform(multipart("/api/v1/ingest/sales/daily")
                .file("file", csv.getBytes(StandardCharsets.UTF_8))
                .param("storeId", "1"))
            // no X-API-Key header
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
