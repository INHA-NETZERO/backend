package com.netzero.weather;

import com.netzero.weather.dto.DailyWeather;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.time.LocalDate;
import java.util.Optional;

@Configuration
class NoOpWeatherProvider {
    @Bean
    @ConditionalOnMissingBean(WeatherProvider.class)
    WeatherProvider weatherProvider() {
        return (storeId, date) -> Optional.empty();
    }
}
