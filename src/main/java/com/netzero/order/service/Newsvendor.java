package com.netzero.order.service;

import java.util.List;

public final class Newsvendor {
    private Newsvendor() {}

    public record Quantiles(double p10, double p50, double p90) {}

    public static double criticalRatio(double cu, double co) {
        return cu / (cu + co);
    }

    public static Quantiles sumDaily(List<Quantiles> daily) {
        double a = 0, b = 0, c = 0;
        for (var q : daily) {
            a += q.p10();
            b += q.p50();
            c += q.p90();
        }
        return new Quantiles(a, b, c);
    }

    public static double roundToLot(double qty, double lot) {
        return lot <= 0 ? qty : Math.ceil(qty / lot) * lot;
    }

    public static double optimalStock(Quantiles horizon, double cu, double co) {
        return QuantileInterpolator.interpolate(
            horizon.p10(),
            horizon.p50(),
            horizon.p90(),
            criticalRatio(cu, co)
        );
    }

    public static double recommendedOrder(Quantiles horizon, double cu, double co, double onHand, double lot) {
        return roundToLot(Math.max(0, optimalStock(horizon, cu, co) - onHand), lot);
    }
}
