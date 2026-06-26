package com.netzero.weather;

import com.netzero.weather.port.KmaForecastPort;
import com.netzero.weather.port.KmaResponse;
import com.netzero.weather.repository.WeatherForecastRepository;
import com.netzero.weather.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WeatherService 단위 테스트.
 * KmaForecastPort 를 목(mock)으로 교체하여 avgTemp 계산과 WeatherForecast 저장을 검증한다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "external.kma.enabled=true",
        "external.kma.base-url=https://test.kma.go.kr"
})
class WeatherServiceTest {

    @MockBean
    KmaForecastPort kmaForecastPort;

    @Autowired
    WeatherService weatherService;

    @Autowired
    WeatherForecastRepository repo;

    @Test
    void fetchAndStoreComputesAvgTempAndPersists() {
        // given: KMA 응답 TMX=24, TMN=18 → avgTemp=21.0
        when(kmaForecastPort.getVillageForecast(any(Map.class)))
                .thenReturn(buildKmaResponse("20260628", "24.0", "18.0", "3", "10", "0mm"));

        // when
        var snapshot = weatherService.fetchAndStore(1L, LocalDate.parse("2026-06-28"));

        // then: avgTemp=(24+18)/2=21.0
        assertThat(snapshot.avgTemp()).isEqualByComparingTo("21.0");
        assertThat(snapshot.forecastDate()).isEqualTo(LocalDate.parse("2026-06-28"));
        // WeatherForecast 가 DB에 저장됐는지 확인
        assertThat(repo.findAll()).hasSize(1);
        assertThat(repo.findAll().get(0).getAvgTemp()).isEqualByComparingTo("21.0");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static KmaResponse buildKmaResponse(String fcstDate, String tmx, String tmn,
                                                 String sky, String pop, String pcp) {
        var items = List.of(
                new KmaResponse.KmaItem("TMX", fcstDate, "1500", tmx),
                new KmaResponse.KmaItem("TMN", fcstDate, "0600", tmn),
                new KmaResponse.KmaItem("SKY", fcstDate, "1500", sky),
                new KmaResponse.KmaItem("POP", fcstDate, "1500", pop),
                new KmaResponse.KmaItem("PCP", fcstDate, "1500", pcp)
        );
        return new KmaResponse(
                new KmaResponse.KmaResponseBody(
                        new KmaResponse.KmaHeader("00", "NORMAL_SERVICE"),
                        new KmaResponse.KmaBody(new KmaResponse.KmaItems(items))
                )
        );
    }
}
