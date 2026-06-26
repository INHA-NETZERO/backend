package com.netzero.forecast.dto;

import java.util.Map;

public record ForecastRow(Long itemId, int orderCycleDays, int leadTimeDays, Map<String, Object> features) {}
