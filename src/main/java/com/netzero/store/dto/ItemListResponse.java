package com.netzero.store.dto;

import java.util.List;

public record ItemListResponse(int count, List<ItemMasterResponse> items) {}
