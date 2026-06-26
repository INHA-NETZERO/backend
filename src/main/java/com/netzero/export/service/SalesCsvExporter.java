package com.netzero.export.service;

import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class SalesCsvExporter {

    private final SalesRecordRepository repo;

    public SalesCsvExporter(SalesRecordRepository repo) {
        this.repo = repo;
    }

    public void export(Long storeId, LocalDate from, LocalDate to, OutputStream out) throws IOException {
        var writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.println("날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목ID,구분,판매수량,비고_시나리오");
        for (var r : repo.findByStoreIdAndBusinessDateBetween(storeId, from, to)) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                r.getBusinessDate(), safe(r.getDayOfWeek()), safe(r.getWeather()),
                safe(r.getAvgTemp()), safe(r.getPrecipitationMm()),
                safe(r.getEvent()), safe(r.getNewMenu()),
                r.getItemId(), safe(r.getCategory()), r.getQuantitySold(), safe(r.getScenarioNote()));
        }
        writer.flush();
    }

    private String safe(Object v) {
        return v == null ? "" : v.toString();
    }
}
