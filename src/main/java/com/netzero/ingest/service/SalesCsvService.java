package com.netzero.ingest.service;

import com.netzero.ingest.CsvParser;
import com.netzero.ingest.dto.DailyIngestResult;
import com.netzero.ingest.dto.IngestResult;
import com.netzero.store.domain.SalesRecord;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import com.netzero.weather.WeatherProvider;
import com.netzero.weather.dto.DailyWeather;

@Service
public class SalesCsvService {
    private final ItemMasterRepository items;
    private final SalesRecordRepository sales;
    private final WeatherProvider weather;

    public SalesCsvService(ItemMasterRepository i, SalesRecordRepository s, WeatherProvider w) {
        this.items = i;
        this.sales = s;
        this.weather = w;
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
            var rec = new SalesRecord(storeId, LocalDate.parse(r.get("날짜")), r.get("요일"), r.get("날씨"),
                bd(r.get("기온")), bd(r.get("강수mm")), nz(r.get("행사")), nz(r.get("신메뉴")),
                item.getCategory().name(), item.getId(), bd(r.get("판매수량")), nz(r.get("비고_시나리오")));
            sales.save(rec);
            accepted++;
        }
        return new IngestResult(accepted, errors.size(), errors);
    }

    @Transactional
    public DailyIngestResult ingestDaily(Long storeId, InputStream csv) {
        var rows = CsvParser.parse(csv);
        int accepted = 0;
        var errors = new ArrayList<IngestResult.RowError>();
        LocalDate applied = null;
        int line = 1;
        var weatherCache = new HashMap<LocalDate, Optional<DailyWeather>>();
        for (var r : rows) {
            line++;
            var item = items.findByName(r.get("품목")).orElse(null);
            if (item == null) {
                errors.add(new IngestResult.RowError(line, "ITEM_NOT_FOUND", r.get("품목")));
                continue;
            }
            LocalDate d = LocalDate.parse(r.get("날짜"));
            applied = d;
            // 기상청 날씨 보강(날짜당 1회 조회, 실패/미연동 시 null)
            var optW = weatherCache.computeIfAbsent(d, dd -> weather.lookup(storeId, dd));
            var w = optW.orElse(null);
            String wx = w != null ? w.weather() : null;
            BigDecimal temp = w != null ? w.avgTemp() : null;
            BigDecimal precip = w != null ? w.precipitationMm() : null;
            // 행사/신메뉴/비고_시나리오는 행(품목)별 컬럼에서 읽음
            var rec = new SalesRecord(storeId, d, r.get("요일"), wx, temp, precip,
                nz(r.get("행사")), nz(r.get("신메뉴")), item.getCategory().name(), item.getId(),
                bd(r.get("판매수량")), nz(r.get("비고_시나리오")));
            sales.save(rec);
            accepted++;
        }
        return new DailyIngestResult(applied, accepted, errors.size(), errors);
    }

    private static BigDecimal bd(String s) {
        return s == null || s.isBlank() ? null : new BigDecimal(s);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
