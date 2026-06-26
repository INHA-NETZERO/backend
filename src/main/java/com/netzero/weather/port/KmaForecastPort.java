package com.netzero.weather.port;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import java.util.Map;

/**
 * 기상청 단기예보 조회서비스 HTTP 인터페이스.
 * RestClientAdapter + HttpServiceProxyFactory 를 통해 프록시 생성.
 */
public interface KmaForecastPort {

    @GetExchange("/getVilageFcst")
    KmaResponse getVillageForecast(@RequestParam Map<String, String> q);
}
