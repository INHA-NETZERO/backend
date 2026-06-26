package com.netzero.weather.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_forecast")
public class WeatherForecast extends BaseEntity {

    private String region;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "temp_max")
    private BigDecimal tempMax;

    @Column(name = "temp_min")
    private BigDecimal tempMin;

    @Column(name = "avg_temp")
    private BigDecimal avgTemp;

    @Column(name = "precipitation_mm")
    private BigDecimal precipitationMm;

    @Column(name = "precipitation_prob")
    private Integer precipitationProb;

    @Column(name = "sky_code")
    private Integer skyCode;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    protected WeatherForecast() {}

    public WeatherForecast(String region, LocalDate forecastDate, BigDecimal tempMax, BigDecimal tempMin,
                           BigDecimal avgTemp, BigDecimal precipitationMm, Integer precipitationProb,
                           Integer skyCode, LocalDateTime fetchedAt) {
        this.region = region;
        this.forecastDate = forecastDate;
        this.tempMax = tempMax;
        this.tempMin = tempMin;
        this.avgTemp = avgTemp;
        this.precipitationMm = precipitationMm;
        this.precipitationProb = precipitationProb;
        this.skyCode = skyCode;
        this.fetchedAt = fetchedAt;
    }

    public String getRegion() { return region; }
    public LocalDate getForecastDate() { return forecastDate; }
    public BigDecimal getTempMax() { return tempMax; }
    public BigDecimal getTempMin() { return tempMin; }
    public BigDecimal getAvgTemp() { return avgTemp; }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public Integer getPrecipitationProb() { return precipitationProb; }
    public Integer getSkyCode() { return skyCode; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
}
