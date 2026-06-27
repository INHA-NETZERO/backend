package com.netzero.forecast;

import com.netzero.forecast.dto.*;
import com.netzero.forecast.port.ForecastPort;
import com.netzero.weather.dto.WeatherSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AiForecastClient 통합 테스트.
 *
 * MockRestServiceServer 를 RestClient.Builder 에 바인딩해야 하므로,
 * 내부 @TestConfiguration 의 정적 초기화 단계에서 미리 바인딩한 뒤
 * 해당 Builder 로 RestClient 빈을 생성한다.
 * allow-bean-definition-overriding=true 를 통해 @TestConfiguration 의 빈이
 * HttpClientConfig 의 production 빈을 덮어쓴다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ai.mode=real",
        "spring.main.allow-bean-definition-overriding=true"
})
class AiForecastClientTest {

    /**
     * 정적 초기화 시점: 클래스 로드 → MockRestServiceServer 바인딩 → BUILDER 수정.
     * aiForecastRestClient() 호출 시 BUILDER 에 mock requestFactory 가 적용된 RestClient 를 반환한다.
     * @Primary 로 HttpClientConfig 의 동일 이름 빈을 대체한다.
     */
    @TestConfiguration
    static class MockAiRestClientConfig {
        private static final RestClient.Builder BUILDER =
                RestClient.builder().baseUrl("http://localhost:8000");
        static final MockRestServiceServer SERVER =
                MockRestServiceServer.bindTo(BUILDER).build();

        @Bean
        @Primary
        public RestClient aiForecastRestClient() {
            return BUILDER.build();
        }
    }

    @Autowired
    private ForecastPort forecastPort;

    private static final String RESPONSE_FIXTURE = """
            {
              "modelVersion": "v1.0",
              "predictions": [
                {
                  "itemId": 1,
                  "daily": [
                    {"date": "2026-06-27", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-06-28", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-06-29", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-06-30", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-07-01", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-07-02", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-07-03", "p10": 8.0, "p50": 10.0, "p90": 13.0},
                    {"date": "2026-07-04", "p10": 8.0, "p50": 10.0, "p90": 13.0}
                  ]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        MockAiRestClientConfig.SERVER.reset();
    }

    @AfterEach
    void verifyServer() {
        MockAiRestClientConfig.SERVER.verify();
    }

    @Test
    void orderRecommendation_deserializesEightDailyPredictions() {
        MockAiRestClientConfig.SERVER
                .expect(requestTo("http://localhost:8000/v1/order-recommendation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.salesHistory.presignedUrls[0]").exists())
                .andExpect(jsonPath("$.salesHistory.format").value("sales_csv_v1"))
                .andExpect(jsonPath("$.weather[0].avgTemp").exists())
                .andRespond(withSuccess(RESPONSE_FIXTURE, MediaType.APPLICATION_JSON));

        ForecastRequest req = new ForecastRequest(
                1L,
                LocalDate.of(2026, 6, 26),
                new SalesHistory(List.of("https://s3.example.com/sales-2026.csv"), "sales_csv_v1"),
                new CoverageSpec(7, 1, 8),
                List.of(new WeatherSnapshot(
                        LocalDate.of(2026, 6, 27),
                        new BigDecimal("21.5"),
                        BigDecimal.ZERO,
                        10,
                        1
                )),
                List.of(new ForecastRow(1L, 1, 7, Map.of("ma7", 10.0)))
        );

        ForecastResponse response = forecastPort.orderRecommendation(req);

        assertThat(response.predictions()).hasSize(1);
        assertThat(response.predictions().get(0).daily()).hasSize(8);
        assertThat(response.modelVersion()).isEqualTo("v1.0");
    }
}
