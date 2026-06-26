package com.netzero.forecast.port;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.forecast.dto.ForecastRequest;
import com.netzero.forecast.dto.ForecastResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * AI 서버(FastAPI)에 POST /v1/order-recommendation 을 호출하는 실 ForecastPort 구현.
 * ai.mode=real 프로퍼티가 설정된 경우에만 활성화되며, @Primary 로 MockForecastClient 보다 우선한다.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ai.mode", havingValue = "real")
public class AiForecastClient implements ForecastPort {

    private final RestClient restClient;
    private final String recommendationPath;

    public AiForecastClient(
            @Qualifier("aiForecastRestClient") RestClient restClient,
            @Value("${ai.order-recommendation-path:/v1/order-recommendation}") String recommendationPath) {
        this.restClient = restClient;
        this.recommendationPath = recommendationPath;
    }

    @Override
    @CircuitBreaker(name = "aiForecast", fallbackMethod = "fallback")
    public ForecastResponse orderRecommendation(ForecastRequest req) {
        try {
            return restClient.post()
                    .uri(recommendationPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(ForecastResponse.class);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.FORECAST_UNAVAILABLE, "AI server error: " + e.getMessage());
        }
    }

    // Resilience4j fallback — called when circuit is open or the decorated method throws
    public ForecastResponse fallback(ForecastRequest req, Throwable t) {
        throw new ApiException(ErrorCode.FORECAST_UNAVAILABLE, "AI server unavailable (circuit open)");
    }
}
