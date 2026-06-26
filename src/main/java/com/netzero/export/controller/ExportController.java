package com.netzero.export.controller;

import com.netzero.export.service.InventoryFlowExporter;
import com.netzero.export.service.SalesCsvExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final SalesCsvExporter salesExporter;
    private final InventoryFlowExporter inventoryExporter;

    public ExportController(SalesCsvExporter salesExporter, InventoryFlowExporter inventoryExporter) {
        this.salesExporter = salesExporter;
        this.inventoryExporter = inventoryExporter;
    }

    @GetMapping(value = "/sales.csv", produces = "text/csv")
    public void salesCsv(
        @RequestParam Long storeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        HttpServletResponse response
    ) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"sales.csv\"");
        salesExporter.export(storeId, from, to, response.getOutputStream());
    }

    @GetMapping(value = "/store-inventory.csv", produces = "text/csv")
    public void inventoryCsv(
        @RequestParam Long storeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        HttpServletResponse response
    ) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"store-inventory.csv\"");
        inventoryExporter.export(storeId, date, response.getOutputStream());
    }
}
