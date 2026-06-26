package com.netzero.config;

import com.netzero.weather.port.KmaForecastPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * HTTP 클라이언트 빈 등록.
 * - KmaForecastPort: external.kma.enabled=true 일 때만 등록
 * - aiForecastRestClient: ai.mode=real 일 때만 등록
 */
@Configuration
public class HttpClientConfig {

    @Value("${external.kma.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}")
    private String kmaBaseUrl;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Bean
    @ConditionalOnProperty(name = "external.kma.enabled", havingValue = "true")
    public KmaForecastPort kmaForecastPort() {
        RestClient client = RestClient.builder()
                .baseUrl(kmaBaseUrl)
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(client);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(KmaForecastPort.class);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.mode", havingValue = "real")
    public RestClient aiForecastRestClient() {
        return RestClient.builder()
                .baseUrl(aiBaseUrl)
                .build();
    }
}
