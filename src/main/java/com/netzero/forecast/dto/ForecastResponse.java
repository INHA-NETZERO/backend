package com.netzero.forecast.dto;

import java.util.List;

public record ForecastResponse(String modelVersion, List<ItemForecast> predictions) {}
