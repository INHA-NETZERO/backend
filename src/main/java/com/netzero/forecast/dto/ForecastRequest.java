package com.netzero.forecast.dto;

import com.netzero.weather.dto.WeatherSnapshot;
import java.time.LocalDate;
import java.util.List;

public record ForecastRequest(
    Long storeId,
    LocalDate targetDate,
    SalesHistory salesHistory,
    CoverageSpec coverage,
    List<WeatherSnapshot> weather,
    List<ForecastRow> rows
) {}
