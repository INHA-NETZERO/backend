package com.netzero.carbon.dto;

import java.util.List;

public record CarbonSavingsResponse(
        List<CarbonSeriesPoint> series
) {}
