package com.netzero.ingest;

import com.netzero.ingest.service.SalesCsvService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

@SpringBootTest
@Transactional
class SalesCsvServiceTest {

    @Autowired
    SalesCsvService svc;

    @Test
    void parsesValidRowsAndRejectsUnknownItem() {
        String csv = "날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오\n" +
                     "2026-06-01,월,맑음,20.5,0,,,우유,원재료,9,평범한날\n" +
                     "2026-06-01,월,맑음,20.5,0,,,없는품목,원재료,3,오류행\n";
        var r = svc.ingest(1L, new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.assertj.core.api.Assertions.assertThat(r.accepted()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(r.rejected()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(r.errors().get(0).code()).isEqualTo("ITEM_NOT_FOUND");
    }

    @Test
    void dailyUploadAppliesPerItemMeta() {
        String csv = "날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오\n" +
                     "2026-06-28,일,우유,원재료,11,,,주말+맑음->수요 높음\n" +
                     "2026-06-28,일,베이커리,완제품,23,Y,Y,신메뉴 출시+행사\n";
        var r = svc.ingestDaily(1L, new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.assertj.core.api.Assertions.assertThat(r.accepted()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(r.appliedDate()).isEqualTo(java.time.LocalDate.parse("2026-06-28"));
        // 품목별로 다르게 저장됨: 우유는 event=null, 베이커리는 event="Y"
    }
}
