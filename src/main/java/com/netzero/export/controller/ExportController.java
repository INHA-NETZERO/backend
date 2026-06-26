package com.netzero.export.controller;

import com.netzero.common.ApiResponse;
import com.netzero.common.error.ErrorCode;
import com.netzero.export.scheduler.MonthlyExportScheduler;
import com.netzero.export.service.InventoryFlowExporter;
import com.netzero.export.service.PresignService;
import com.netzero.export.service.SalesCsvExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final SalesCsvExporter salesExporter;
    private final InventoryFlowExporter inventoryExporter;
    private final MonthlyExportScheduler scheduler;
    private final PresignService presignService;

    public ExportController(SalesCsvExporter salesExporter,
                            InventoryFlowExporter inventoryExporter,
                            MonthlyExportScheduler scheduler,
                            PresignService presignService) {
        this.salesExporter = salesExporter;
        this.inventoryExporter = inventoryExporter;
        this.scheduler = scheduler;
        this.presignService = presignService;
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

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadMonth(DateTimeParseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Invalid month format: use YYYY-MM"));
    }

    /**
     * Manually trigger a monthly S3 archive for a specific store and month.
     *
     * @param storeId store identifier
     * @param month   target month in {@code YYYY-MM} format (e.g. {@code 2026-06})
     * @return presigned URLs for the uploaded sales and inventory CSVs
     */
    @PostMapping("/archive")
    public ResponseEntity<ApiResponse<Map<String, String>>> archiveManual(
        @RequestParam Long storeId,
        @RequestParam String month
    ) throws IOException {
        YearMonth ym = YearMonth.parse(month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        scheduler.exportStore(storeId, from, to);

        String salesUrl = presignService.presignGet(PresignService.salesKey(storeId, from));
        String inventoryUrl = presignService.presignGet(PresignService.inventoryKey(storeId, from));

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "salesUrl", salesUrl,
            "inventoryUrl", inventoryUrl
        )));
    }
}
