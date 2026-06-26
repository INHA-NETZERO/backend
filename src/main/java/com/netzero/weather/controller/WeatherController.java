package com.netzero.weather.controller;

import com.netzero.common.ApiResponse;
import com.netzero.weather.dto.WeatherSnapshot;
import com.netzero.weather.service.WeatherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 기상청 예보 수동 갱신 엔드포인트.
 * KMA 연동이 활성화된 경우에만 등록된다.
 */
@RestController
@ConditionalOnProperty(name = "external.kma.enabled", havingValue = "true")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * POST /api/v1/weather/refresh?storeId=1&date=2026-06-28
     * 해당 매장·날짜의 기상청 예보를 즉시 조회·저장한다.
     */
    @PostMapping("/api/v1/weather/refresh")
    public ApiResponse<WeatherSnapshot> refresh(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        WeatherSnapshot snapshot = weatherService.fetchAndStore(storeId, date);
        return ApiResponse.ok(snapshot);
    }
}
