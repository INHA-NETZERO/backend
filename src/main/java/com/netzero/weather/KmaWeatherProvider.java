package com.netzero.weather;

import com.netzero.weather.dto.DailyWeather;
import com.netzero.weather.service.WeatherService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 기상청 연동 WeatherProvider 구현체.
 *
 * external.kma.enabled=true 일 때만 빈으로 등록되며 @Primary 를 통해
 * NoOpWeatherProvider(@ConditionalOnMissingBean)를 대체한다.
 *
 * 날씨 매핑 규칙:
 *   precipitationMm > 0  → "비"
 *   else skyCode >= 3    → "흐림"
 *   else                 → "맑음"
 *
 * KMA 호출 실패 시 Optional.empty() 를 반환하여 업로드 자체는 성공하도록 한다.
 */
@Component
@Primary
@ConditionalOnProperty(name = "external.kma.enabled", havingValue = "true")
public class KmaWeatherProvider implements WeatherProvider {

    private final WeatherService weather;

    public KmaWeatherProvider(WeatherService weather) {
        this.weather = weather;
    }

    @Override
    public Optional<DailyWeather> lookup(Long storeId, LocalDate date) {
        try {
            var s = weather.fetchAndStore(storeId, date);
            String wx = (s.precipitationMm() != null && s.precipitationMm().signum() > 0) ? "비"
                    : (s.skyCode() != null && s.skyCode() >= 3) ? "흐림"
                    : "맑음";
            return Optional.of(new DailyWeather(wx, s.avgTemp(), s.precipitationMm()));
        } catch (Exception e) {
            // KMA 실패 시 날씨 null, 업로드는 성공
            return Optional.empty();
        }
    }
}
