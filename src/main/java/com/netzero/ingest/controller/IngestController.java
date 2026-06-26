package com.netzero.ingest.controller;

import com.netzero.common.ApiResponse;
import com.netzero.ingest.dto.DailyIngestResult;
import com.netzero.ingest.dto.IngestResult;
import com.netzero.ingest.service.InventoryCsvService;
import com.netzero.ingest.service.SalesCsvService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {
    private final SalesCsvService sales;
    private final InventoryCsvService inventory;

    public IngestController(SalesCsvService s, InventoryCsvService i) {
        this.sales = s;
        this.inventory = i;
    }

    @PostMapping(value = "/sales", consumes = "multipart/form-data")
    public ApiResponse<IngestResult> sales(@RequestParam Long storeId,
                                           @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.ok(sales.ingest(storeId, file.getInputStream()));
    }

    @PostMapping(value = "/sales/daily", consumes = "multipart/form-data")
    public ApiResponse<DailyIngestResult> salesDaily(@RequestParam Long storeId,
                                                     @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.ok(sales.ingestDaily(storeId, file.getInputStream()));
    }

    @PostMapping(value = "/inventory", consumes = "multipart/form-data")
    public ApiResponse<IngestResult> inventory(@RequestParam Long storeId,
                                               @RequestParam MultipartFile file) throws IOException {
        return ApiResponse.ok(inventory.ingest(storeId, file.getInputStream()));
    }
}
