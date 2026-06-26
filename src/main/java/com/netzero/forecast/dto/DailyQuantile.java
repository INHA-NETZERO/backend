package com.netzero.forecast.dto;

import java.time.LocalDate;

public record DailyQuantile(LocalDate date, double p10, double p50, double p90) {}
