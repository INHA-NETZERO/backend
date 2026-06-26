package com.netzero.weather.dto;

import java.math.BigDecimal;

public record DailyWeather(String weather, BigDecimal avgTemp, BigDecimal precipitationMm) {}
