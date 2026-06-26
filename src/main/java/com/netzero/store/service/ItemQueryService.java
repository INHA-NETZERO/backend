package com.netzero.store.service;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.store.domain.ItemCategory;
import com.netzero.store.domain.ItemMaster;
import com.netzero.store.dto.ItemListResponse;
import com.netzero.store.dto.ItemMasterResponse;
import com.netzero.store.repository.ItemMasterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemQueryService {

    private final ItemMasterRepository repo;

    public ItemQueryService(ItemMasterRepository repo) {
        this.repo = repo;
    }

    public ItemListResponse findAll(String category, Boolean wasteTargetOnly) {
        List<ItemMaster> items;
        if (Boolean.TRUE.equals(wasteTargetOnly)) {
            items = repo.findByWasteTargetTrue();
        } else if (category != null) {
            items = repo.findByCategory(ItemCategory.valueOf(category));
        } else {
            items = repo.findAll();
        }
        var responses = items.stream().map(ItemMasterResponse::from).toList();
        return new ItemListResponse(responses.size(), responses);
    }

    public ItemMasterResponse findById(Long id) {
        return repo.findById(id)
                .map(ItemMasterResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.ITEM_NOT_FOUND, "Item not found: " + id));
    }
}
