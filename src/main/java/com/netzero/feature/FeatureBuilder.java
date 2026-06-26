package com.netzero.feature;

import com.netzero.store.repository.SalesRecordRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FeatureBuilder {

    private final SalesRecordRepository salesRecordRepository;
    private final HolidayCalendar holidayCalendar;

    public FeatureBuilder(SalesRecordRepository salesRecordRepository, HolidayCalendar holidayCalendar) {
        this.salesRecordRepository = salesRecordRepository;
        this.holidayCalendar = holidayCalendar;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(Long storeId, Long itemId, LocalDate targetDate) {
        int dayOfWeek = targetDate.getDayOfWeek().getValue(); // 1=Mon, 7=Sun

        // Calculate recent 7-day moving average (last 7 days before targetDate)
        double ma7 = salesRecordRepository
                .findByStoreIdAndBusinessDateBetween(storeId, targetDate.minusDays(7), targetDate.minusDays(1))
                .stream()
                .filter(r -> r.getItemId().equals(itemId))
                .mapToDouble(r -> r.getQuantitySold().doubleValue())
                .average()
                .orElse(0.0);

        // Calculate prior 7-day moving average (days 8-14 before targetDate)
        double ma7Prior = salesRecordRepository
                .findByStoreIdAndBusinessDateBetween(storeId, targetDate.minusDays(14), targetDate.minusDays(8))
                .stream()
                .filter(r -> r.getItemId().equals(itemId))
                .mapToDouble(r -> r.getQuantitySold().doubleValue())
                .average()
                .orElse(0.0);

        // Calculate trend as percentage change from prior period to recent period
        double trend = (ma7 - ma7Prior) / Math.max(ma7Prior, 1.0);

        // Check if targetDate is a holiday
        boolean isHoliday = holidayCalendar.isHoliday(targetDate);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("dayOfWeek", dayOfWeek);
        features.put("isHoliday", isHoliday);
        features.put("ma7", ma7);
        features.put("trend", trend);
        return features;
    }
}
