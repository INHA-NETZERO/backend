package com.netzero.feature;

import com.netzero.store.domain.SalesRecord;
import com.netzero.store.repository.SalesRecordRepository;
import com.netzero.store.repository.ItemMasterRepository;
import com.netzero.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FeatureBuilderTest {

    @Autowired
    private FeatureBuilder featureBuilder;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    @Autowired
    private ItemMasterRepository itemMasterRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Test
    void testNewYearDayIsHoliday() {
        // 2026-01-01 is 신정 (New Year's Day)
        var milk = itemMasterRepository.findByName("우유").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        LocalDate newYearDay = LocalDate.of(2026, 1, 1);
        Map<String, Object> features = featureBuilder.build(store.getId(), milk.getId(), newYearDay);

        assertThat(features).containsKey("isHoliday");
        assertThat(features.get("isHoliday")).isEqualTo(true);
    }

    @Test
    void testRegularMondayIsNotHoliday() {
        // 2026-06-01 is a regular Monday, not a holiday
        var milk = itemMasterRepository.findByName("우유").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        LocalDate regularMonday = LocalDate.of(2026, 6, 1);
        Map<String, Object> features = featureBuilder.build(store.getId(), milk.getId(), regularMonday);

        assertThat(features).containsKey("isHoliday");
        assertThat(features.get("isHoliday")).isEqualTo(false);
        assertThat(features.get("dayOfWeek")).isEqualTo(1); // Monday
    }

    @Test
    void testChildrensDayIsHoliday() {
        // 2026-05-05 is 어린이날 (Children's Day)
        var milk = itemMasterRepository.findByName("우유").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        LocalDate childrenDay = LocalDate.of(2026, 5, 5);
        Map<String, Object> features = featureBuilder.build(store.getId(), milk.getId(), childrenDay);

        assertThat(features.get("isHoliday")).isEqualTo(true);
    }

    @Test
    void testTrendPositiveWhenRecentHigherThanPrior() {
        // Test date: 2026-06-15
        // Recent 7 days (2026-06-08 to 2026-06-14): 100 units each = avg 100
        // Prior 7 days (2026-06-01 to 2026-06-07): 50 units each = avg 50
        // Trend = (100 - 50) / 50 = 1.0 (100% increase)

        var milk = itemMasterRepository.findByName("우유").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        Long storeId = store.getId();
        Long itemId = milk.getId();
        LocalDate targetDate = LocalDate.of(2026, 6, 15);

        // Insert prior 7-day records (2026-06-01 to 2026-06-07)
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(2026, 6, 1).plusDays(i);
            SalesRecord record = new SalesRecord(
                    storeId, date, null, null,
                    null, null, null, null,
                    null, itemId, new BigDecimal("50"), null
            );
            salesRecordRepository.save(record);
        }

        // Insert recent 7-day records (2026-06-08 to 2026-06-14)
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(2026, 6, 8).plusDays(i);
            SalesRecord record = new SalesRecord(
                    storeId, date, null, null,
                    null, null, null, null,
                    null, itemId, new BigDecimal("100"), null
            );
            salesRecordRepository.save(record);
        }

        Map<String, Object> features = featureBuilder.build(storeId, itemId, targetDate);

        assertThat(features).containsKey("trend");
        double trend = (Double) features.get("trend");
        assertThat(trend).isGreaterThan(0.0);
        assertThat(trend).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void testTrendNegativeWhenRecentLowerThanPrior() {
        // Test date: 2026-07-15
        // Recent 7 days (2026-07-08 to 2026-07-14): 50 units each = avg 50
        // Prior 7 days (2026-07-01 to 2026-07-07): 100 units each = avg 100
        // Trend = (50 - 100) / 100 = -0.5 (50% decrease)

        var cheese = itemMasterRepository.findByName("휘핑크림").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        Long storeId = store.getId();
        Long itemId = cheese.getId();
        LocalDate targetDate = LocalDate.of(2026, 7, 15);

        // Insert prior 7-day records (2026-07-01 to 2026-07-07)
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(2026, 7, 1).plusDays(i);
            SalesRecord record = new SalesRecord(
                    storeId, date, null, null,
                    null, null, null, null,
                    null, itemId, new BigDecimal("100"), null
            );
            salesRecordRepository.save(record);
        }

        // Insert recent 7-day records (2026-07-08 to 2026-07-14)
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(2026, 7, 8).plusDays(i);
            SalesRecord record = new SalesRecord(
                    storeId, date, null, null,
                    null, null, null, null,
                    null, itemId, new BigDecimal("50"), null
            );
            salesRecordRepository.save(record);
        }

        Map<String, Object> features = featureBuilder.build(storeId, itemId, targetDate);

        assertThat(features).containsKey("trend");
        double trend = (Double) features.get("trend");
        assertThat(trend).isLessThan(0.0);
        assertThat(trend).isCloseTo(-0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void testTrendZeroWhenNoDataAvailable() {
        // Test with no sales data -> both ma7 and ma7Prior will be 0
        // Trend = (0 - 0) / max(0, 1.0) = 0 / 1.0 = 0.0

        var coffee = itemMasterRepository.findByName("에스프레소 원두").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        Long storeId = store.getId();
        Long itemId = coffee.getId();
        LocalDate targetDate = LocalDate.of(2026, 8, 15);

        Map<String, Object> features = featureBuilder.build(storeId, itemId, targetDate);

        assertThat(features).containsKey("trend");
        double trend = (Double) features.get("trend");
        assertThat(trend).isEqualTo(0.0);
    }

    @Test
    void testMapContainsAllRequiredKeys() {
        // Verify the map structure remains unchanged
        var milk = itemMasterRepository.findByName("우유").orElseThrow();
        var store = storeRepository.findAll().stream().findFirst().orElseThrow();
        LocalDate testDate = LocalDate.of(2026, 6, 1);
        Map<String, Object> features = featureBuilder.build(store.getId(), milk.getId(), testDate);

        assertThat(features).containsKeys("dayOfWeek", "isHoliday", "ma7", "trend");
        assertThat(features).hasSize(4);
    }
}
