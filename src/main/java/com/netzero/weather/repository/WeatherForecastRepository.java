package com.netzero.weather.repository;

import com.netzero.weather.domain.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    Optional<WeatherForecast> findFirstByRegionAndForecastDateOrderByFetchedAtDesc(String region, LocalDate date);
}
