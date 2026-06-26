package com.netzero.store.controller;

import com.netzero.common.ApiResponse;
import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.store.dto.InventoryStatusResponse;
import com.netzero.store.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryQueryService service;

    public InventoryController(InventoryQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<InventoryStatusResponse> status(
            @RequestParam Long storeId,
            @RequestParam String date,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean wasteTargetOnly
    ) {
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Invalid date format: " + date);
        }
        return ApiResponse.ok(service.statusOn(storeId, parsedDate, category, wasteTargetOnly));
    }
}
