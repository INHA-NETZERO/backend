package com.netzero.export.service;

import com.netzero.store.repository.InventorySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class InventoryFlowExporter {

    private final InventorySnapshotRepository repo;

    public InventoryFlowExporter(InventorySnapshotRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public void export(Long storeId, LocalDate date, OutputStream out) throws IOException {
        var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("날짜,요일,품목,구분,단위,발주수량,기초재고,수요량,실판매,결품,폐기량,기말재고,폐기중량kg,폐기탄소kg,폐기비용원,최근발주일");
        for (var s : repo.findByStoreIdAndBusinessDate(storeId, date)) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                s.getBusinessDate(), safe(s.getDayOfWeek()), s.getItem().getName(),
                safe(s.getCategory()), safe(s.getUnit()),
                safe(s.getOrderedQty()), safe(s.getOpeningStock()), safe(s.getDemand()),
                safe(s.getActualSales()), safe(s.getStockout()), safe(s.getWasteQty()),
                safe(s.getClosingStock()), safe(s.getWasteKg()), safe(s.getWasteCarbonKg()),
                safe(s.getWasteCostKrw()), safe(s.getLastOrderDate()));
        }
        writer.flush();
    }

    private String safe(Object v) {
        return v == null ? "" : v.toString();
    }
}
