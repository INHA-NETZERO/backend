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

    public FeatureBuilder(SalesRecordRepository salesRecordRepository) {
        this.salesRecordRepository = salesRecordRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> build(Long storeId, Long itemId, LocalDate targetDate) {
        int dayOfWeek = targetDate.getDayOfWeek().getValue(); // 1=Mon, 7=Sun

        double ma7 = salesRecordRepository
                .findByStoreIdAndBusinessDateBetween(storeId, targetDate.minusDays(7), targetDate.minusDays(1))
                .stream()
                .filter(r -> r.getItemId().equals(itemId))
                .mapToDouble(r -> r.getQuantitySold().doubleValue())
                .average()
                .orElse(0.0);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("dayOfWeek", dayOfWeek);
        features.put("isHoliday", false);
        features.put("ma7", ma7);
        features.put("trend", 0.0);
        return features;
    }
}
