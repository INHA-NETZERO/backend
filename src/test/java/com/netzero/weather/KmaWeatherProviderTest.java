package com.netzero.weather;

import com.netzero.ingest.service.SalesCsvService;
import com.netzero.store.repository.SalesRecordRepository;
import com.netzero.weather.dto.WeatherSnapshot;
import com.netzero.weather.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * KmaWeatherProvider 통합 테스트.
 *
 * WeatherService 를 목으로 교체하고 KmaWeatherProvider(@Primary) 를 통해
 * daily 업로드 시 날씨 보강이 올바르게 이루어지는지 검증한다.
 *
 * 시나리오: precipitationMm=0, skyCode=4(흐림) → weather="흐림", avgTemp=21.0
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "external.kma.enabled=true",
        "external.kma.base-url=https://test.kma.go.kr"
})
class KmaWeatherProviderTest {

    @MockBean
    WeatherService weatherService;

    @Autowired
    SalesCsvService sales;

    @Autowired
    SalesRecordRepository repo;

    @Test
    void dailyUploadEnrichesWeatherFromKma() {
        // given: 기상청 결과(강수 0, 흐림 skyCode=4) → "흐림", avgTemp 21.0
        when(weatherService.fetchAndStore(eq(1L), eq(LocalDate.parse("2026-06-28"))))
                .thenReturn(new WeatherSnapshot(
                        LocalDate.parse("2026-06-28"),
                        new BigDecimal("21.0"),
                        BigDecimal.ZERO,
                        10,
                        4));

        String csv = "날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오\n"
                + "2026-06-28,일,우유,원재료,11,,,\n";
        sales.ingestDaily(1L, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        var rec = repo.findByStoreIdAndBusinessDateBetween(
                1L,
                LocalDate.parse("2026-06-28"),
                LocalDate.parse("2026-06-28")).get(0);

        assertThat(rec.getWeather()).isEqualTo("흐림");
        assertThat(rec.getAvgTemp()).isEqualByComparingTo("21.0");
    }
}
