package com.netzero.order.service;

public final class QuantileInterpolator {
    private QuantileInterpolator() {}

    /**
     * Interpolates quantile values based on confidence ratio (CR) using piecewise linear interpolation.
     *
     * Rules (backend_spec §4.3):
     * - CR ≤ 0.10 → p10 (extrapolate low)
     * - 0.10 < CR ≤ 0.50 → interpolate between p10 and p50
     * - 0.50 < CR ≤ 0.90 → interpolate between p50 and p90
     * - CR > 0.90 → p90 (extrapolate high)
     *
     * @param p10 10th percentile
     * @param p50 50th percentile (median)
     * @param p90 90th percentile
     * @param cr confidence ratio in [0, 1]
     * @return interpolated value
     */
    public static double interpolate(double p10, double p50, double p90, double cr) {
        if (cr <= 0.10) return p10;
        if (cr <= 0.50) return p10 + (cr - 0.10) / 0.40 * (p50 - p10);
        if (cr <= 0.90) return p50 + (cr - 0.50) / 0.40 * (p90 - p50);
        return p90;
    }
}
