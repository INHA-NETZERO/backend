package com.netzero.weather;

import com.netzero.weather.dto.DailyWeather;
import java.time.LocalDate;
import java.util.Optional;

public interface WeatherProvider {
    Optional<DailyWeather> lookup(Long storeId, LocalDate date);
}
