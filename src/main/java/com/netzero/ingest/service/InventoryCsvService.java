package com.netzero.ingest.service;

import com.netzero.ingest.CsvParser;
import com.netzero.ingest.dto.IngestResult;
import com.netzero.store.domain.InventorySnapshot;
import com.netzero.store.repository.InventorySnapshotRepository;
import com.netzero.store.repository.ItemMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

@Service
public class InventoryCsvService {

    private final ItemMasterRepository items;
    private final InventorySnapshotRepository repo;

    public InventoryCsvService(ItemMasterRepository items, InventorySnapshotRepository repo) {
        this.items = items;
        this.repo = repo;
    }

    @Transactional
    public IngestResult ingest(Long storeId, InputStream csv) {
        var rows = CsvParser.parse(csv);
        int accepted = 0;
        var errors = new ArrayList<IngestResult.RowError>();
        int line = 1;
        for (var r : rows) {
            line++;
            var item = items.findByName(r.get("품목")).orElse(null);
            if (item == null) {
                errors.add(new IngestResult.RowError(line, "ITEM_NOT_FOUND", r.get("품목")));
                continue;
            }
            var businessDate = LocalDate.parse(r.get("날짜"));
            var snap = repo.findByStoreIdAndItem_IdAndBusinessDate(storeId, item.getId(), businessDate)
                .orElseGet(InventorySnapshot::new);
            snap.setStoreId(storeId);
            snap.setBusinessDate(businessDate);
            snap.setDayOfWeek(r.get("요일"));
            snap.setItem(item);
            snap.setCategory(r.get("구분"));
            snap.setUnit(r.get("단위"));
            snap.setOrderedQty(bd(r.get("발주수량")));
            snap.setOpeningStock(bd(r.get("기초재고")));
            snap.setDemand(bd(r.get("수요량")));
            snap.setActualSales(bd(r.get("실판매")));
            snap.setStockout(bd(r.get("결품")));
            snap.setWasteQty(bd(r.get("폐기량")));
            snap.setClosingStock(bd(r.get("기말재고")));
            snap.setWasteKg(bd(r.get("폐기중량kg")));
            snap.setWasteCarbonKg(bd(r.get("폐기탄소kg")));
            snap.setWasteCostKrw(bd(r.get("폐기비용원")));
            String lod = r.get("최근발주일");
            snap.setLastOrderDate(lod == null || lod.isBlank() ? null : LocalDate.parse(lod));
            repo.save(snap);
            accepted++;
        }
        return new IngestResult(accepted, errors.size(), errors);
    }

    private static BigDecimal bd(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }
}
