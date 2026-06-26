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
 * KMA 연동이 활성화된 경우에만 RestClient 기반 HTTP 프록시 빈을 등록한다.
 * external.kma.enabled=false(기본값)일 때는 이 설정 클래스 자체가 로드되지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "external.kma.enabled", havingValue = "true")
public class HttpClientConfig {

    @Value("${external.kma.base-url}")
    private String kmaBaseUrl;

    @Bean
    public KmaForecastPort kmaForecastPort() {
        RestClient client = RestClient.builder()
                .baseUrl(kmaBaseUrl)
                .build();
        RestClientAdapter adapter = RestClientAdapter.create(client);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(KmaForecastPort.class);
    }
}
