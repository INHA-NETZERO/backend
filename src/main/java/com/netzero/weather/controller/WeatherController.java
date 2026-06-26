package com.netzero.weather.controller;

import com.netzero.common.ApiResponse;
import com.netzero.common.error.ErrorCode;
import com.netzero.weather.dto.WeatherSnapshot;
import com.netzero.weather.service.WeatherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<WeatherSnapshot>> refresh(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            WeatherSnapshot snapshot = weatherService.fetchAndStore(storeId, date);
            return ResponseEntity.ok(ApiResponse.ok(snapshot));
        } catch (Exception e) {
            @SuppressWarnings("unchecked")
            ApiResponse<WeatherSnapshot> errorResponse = (ApiResponse<WeatherSnapshot>) (ApiResponse<?>)
                    ApiResponse.error(ErrorCode.WEATHER_FETCH_FAILED, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
        }
    }
}
