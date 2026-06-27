package com.netzero.chat;

import com.netzero.carbon.domain.CarbonSaving;
import com.netzero.carbon.repository.CarbonSavingRepository;
import com.netzero.chat.port.Grounding;
import com.netzero.chat.service.RagContextAssembler;
import com.netzero.forecast.domain.DemandForecast;
import com.netzero.forecast.repository.DemandForecastRepository;
import com.netzero.order.domain.OrderRecommendation;
import com.netzero.order.repository.OrderRecommendationRepository;
import com.netzero.store.repository.ItemMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RagContextAssembler 통합 테스트.
 * Flyway 시드 데이터(item_master, store)를 활용하고
 * 테스트 내에서 forecast/recommendation/carbon 레코드를 생성한다.
 */
@SpringBootTest
@Transactional
class RagContextAssemblerTest {

    @Autowired
    private RagContextAssembler assembler;

    @Autowired
    private ItemMasterRepository itemMasterRepo;

    @Autowired
    private DemandForecastRepository demandForecastRepo;

    @Autowired
    private OrderRecommendationRepository orderRecommendationRepo;

    @Autowired
    private CarbonSavingRepository carbonSavingRepo;

    private static final Long STORE_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 1);

    private Long itemId;

    @BeforeEach
    void setUp() {
        // Use the seeded "우유" item (id=1 from V2__seed_item_master.sql)
        itemId = itemMasterRepo.findByName("우유")
                .orElseThrow(() -> new IllegalStateException("Seed item '우유' not found"))
                .getId();

        DemandForecast df = new DemandForecast();
        df.setStoreId(STORE_ID);
        df.setItemId(itemId);
        df.setTargetDate(TEST_DATE);
        df.setP10(new BigDecimal("8.0"));
        df.setP50(new BigDecimal("10.0"));
        df.setP90(new BigDecimal("13.0"));
        df.setModelVersion("v1-test");
        demandForecastRepo.save(df);

        OrderRecommendation or = new OrderRecommendation();
        or.setStoreId(STORE_ID);
        or.setItemId(itemId);
        or.setTargetDate(TEST_DATE);
        or.setRecommendedQuantity(new BigDecimal("12.0"));
        or.setCriticalRatio(new BigDecimal("0.75"));
        or.setExpectedWasteAvoidedKg(new BigDecimal("2.5"));
        orderRecommendationRepo.save(or);

        CarbonSaving cs = new CarbonSaving();
        cs.setStoreId(STORE_ID);
        cs.setItemId(itemId);
        cs.setTargetDate(TEST_DATE);
        cs.setGuaranteedSavingKg(new BigDecimal("1.2"));
        cs.setPotentialSavingKg(new BigDecimal("3.8"));
        cs.setWasteAvoidedKg(new BigDecimal("2.4"));
        carbonSavingRepo.save(cs);
    }

    @Test
    void assemble_itemContainsNameKey() {
        Grounding grounding = assembler.assemble(STORE_ID, TEST_DATE, itemId);
        assertThat(grounding.item()).containsKey("itemName");
        assertThat(grounding.item().get("itemName")).isEqualTo("우유");
        assertThat(grounding.item()).containsKey("itemId");
    }

    @Test
    void assemble_recommendationContainsRecommendedQuantity() {
        Grounding grounding = assembler.assemble(STORE_ID, TEST_DATE, itemId);
        assertThat(grounding.recommendation()).containsKey("recommendedQuantity");
    }

    @Test
    void assemble_carbonContainsGuaranteedSavingKg() {
        Grounding grounding = assembler.assemble(STORE_ID, TEST_DATE, itemId);
        assertThat(grounding.carbon()).containsKey("guaranteedSavingKg");
    }

    @Test
    void assemble_forecastContainsQuantiles() {
        Grounding grounding = assembler.assemble(STORE_ID, TEST_DATE, itemId);
        assertThat(grounding.forecast()).containsKeys("p10", "p50", "p90");
    }

    @Test
    void assemble_contextContainsDateAndStoreId() {
        Grounding grounding = assembler.assemble(STORE_ID, TEST_DATE, itemId);
        assertThat(grounding.context()).containsEntry("storeId", STORE_ID);
        assertThat(grounding.context()).containsEntry("date", TEST_DATE.toString());
    }
}
