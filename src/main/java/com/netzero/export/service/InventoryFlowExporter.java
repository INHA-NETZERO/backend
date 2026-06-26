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
        out.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
        var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.print("날짜,요일,품목,구분,단위,발주수량,기초재고,수요량,실판매,결품,폐기량,기말재고,폐기중량kg,폐기탄소kg,폐기비용원,최근발주일\r\n");
        for (var s : repo.findByStoreIdAndBusinessDate(storeId, date)) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n",
                q(s.getBusinessDate()), q(s.getDayOfWeek()), q(s.getItem().getName()),
                q(s.getCategory()), q(s.getUnit()),
                q(s.getOrderedQty()), q(s.getOpeningStock()), q(s.getDemand()),
                q(s.getActualSales()), q(s.getStockout()), q(s.getWasteQty()),
                q(s.getClosingStock()), q(s.getWasteKg()), q(s.getWasteCarbonKg()),
                q(s.getWasteCostKrw()), q(s.getLastOrderDate()));
        }
        writer.flush();
    }

    private static String q(Object v) {
        if (v == null) return "";
        String s = v.toString();
        if (v instanceof String) {
            if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
                s = "\t" + s;
            }
            if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
        }
        return s;
    }
}
