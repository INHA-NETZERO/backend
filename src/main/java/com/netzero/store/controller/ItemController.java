package com.netzero.store.controller;

import com.netzero.common.ApiResponse;
import com.netzero.store.dto.ItemListResponse;
import com.netzero.store.dto.ItemMasterResponse;
import com.netzero.store.service.ItemQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemQueryService service;

    public ItemController(ItemQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ItemListResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean wasteTargetOnly
    ) {
        return ApiResponse.ok(service.findAll(category, wasteTargetOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemMasterResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.findById(id));
    }
}
