package com.netzero.export.service;

import com.netzero.store.domain.ItemMaster;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class SalesCsvExporter {

    private final SalesRecordRepository repo;
    private final ItemMasterRepository itemRepo;

    public SalesCsvExporter(SalesRecordRepository repo, ItemMasterRepository itemRepo) {
        this.repo = repo;
        this.itemRepo = itemRepo;
    }

    @Transactional(readOnly = true)
    public void export(Long storeId, LocalDate from, LocalDate to, OutputStream out) throws IOException {
        out.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
        var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.print("날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오\r\n");
        for (var r : repo.findByStoreIdAndBusinessDateBetween(storeId, from, to)) {
            String itemName = itemRepo.findById(r.getItemId()).map(ItemMaster::getName).orElse("");
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\r\n",
                q(r.getBusinessDate()), q(r.getDayOfWeek()), q(r.getWeather()),
                q(r.getAvgTemp()), q(r.getPrecipitationMm()),
                q(r.getEvent()), q(r.getNewMenu()),
                q(itemName), q(r.getCategory()), q(r.getQuantitySold()), q(r.getScenarioNote()));
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
