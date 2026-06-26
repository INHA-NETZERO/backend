package com.netzero.forecast.service;

import com.netzero.metrics.ForecastMetrics;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for calculating WAPE (Weighted Absolute Percentage Error).
 * WAPE = Σ|actual - predicted| / Σactual
 */
@Service
public class WapeService {

    private final ForecastMetrics metrics;

    public WapeService(ForecastMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Calculate WAPE (Weighted Absolute Percentage Error).
     * WAPE = Σ|actual - predicted| / Σactual
     *
     * @param actual    list of actual values
     * @param predicted list of predicted values
     * @return WAPE value (0.0 if actual sum is 0 or lists are empty)
     */
    public double wape(List<Double> actual, List<Double> predicted) {
        if (actual == null || predicted == null || actual.isEmpty()) {
            return 0.0;
        }

        double sumActual = 0.0;
        double sumAbsErr = 0.0;
        int n = Math.min(actual.size(), predicted.size());

        for (int i = 0; i < n; i++) {
            sumActual += actual.get(i);
            sumAbsErr += Math.abs(actual.get(i) - predicted.get(i));
        }

        return sumActual == 0.0 ? 0.0 : sumAbsErr / sumActual;
    }

    /**
     * Compute WAPE for an item and record the metric.
     *
     * @param itemCode  the item code/identifier
     * @param actual    list of actual values
     * @param predicted list of predicted values
     * @return WAPE value
     */
    public double computeAndRecord(String itemCode, List<Double> actual, List<Double> predicted) {
        double w = wape(actual, predicted);
        metrics.recordWape(itemCode, w);
        return w;
    }
}
