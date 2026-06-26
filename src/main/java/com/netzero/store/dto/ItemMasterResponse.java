package com.netzero.store.dto;

import com.netzero.store.domain.ItemMaster;

import java.math.BigDecimal;

public record ItemMasterResponse(
        Long id,
        String name,
        String category,
        boolean wasteTarget,
        String unit,
        Integer shelfLifeDays,
        String storageCondition,
        BigDecimal kgPerUnit,
        BigDecimal efProd,
        BigDecimal efWaste,
        BigDecimal purchasePrice,
        String priceUnit,
        String note
) {
    public static ItemMasterResponse from(ItemMaster item) {
        return new ItemMasterResponse(
                item.getId(),
                item.getName(),
                item.getCategory().name(),
                item.isWasteTarget(),
                item.getUnit(),
                item.getShelfLifeDays(),
                item.getStorageCondition() != null ? item.getStorageCondition().name() : null,
                item.getKgPerUnit(),
                item.getEfProd(),
                item.getEfWaste(),
                item.getPurchasePrice(),
                item.getPriceUnit(),
                item.getNote()
        );
    }
}
