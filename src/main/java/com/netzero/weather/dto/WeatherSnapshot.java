package com.netzero.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeatherSnapshot(
    LocalDate forecastDate,
    BigDecimal avgTemp,
    BigDecimal precipitationMm,
    Integer precipitationProb,
    Integer skyCode
) {}
