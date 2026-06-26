package com.netzero.forecast.port;

import com.netzero.forecast.dto.ForecastRequest;
import com.netzero.forecast.dto.ForecastResponse;

public interface ForecastPort {
    ForecastResponse orderRecommendation(ForecastRequest req);
}
