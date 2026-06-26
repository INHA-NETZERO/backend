package com.netzero.forecast.dto;

import java.util.List;

public record ItemForecast(Long itemId, List<DailyQuantile> daily) {}
