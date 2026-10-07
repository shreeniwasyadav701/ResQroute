package com.resqroute.enums;

/**
 * Traffic density of a road segment. The speed factor is applied to the road
 * base speed so that travel time grows as congestion grows. Weighted edges in
 * the routing graph are derived from these factors.
 */
public enum TrafficLevel {
    FREE_FLOW(1.00, 0.00),
    MODERATE(0.80, 0.50),
    HEAVY(0.50, 1.50),
    GRIDLOCK(0.25, 3.00);

    private final double speedFactor;
    private final double congestionPenalty;

    TrafficLevel(double speedFactor, double congestionPenalty) {
        this.speedFactor = speedFactor;
        this.congestionPenalty = congestionPenalty;
    }

    public double getSpeedFactor() {
        return speedFactor;
    }

    public double getCongestionPenalty() {
        return congestionPenalty;
    }
}
