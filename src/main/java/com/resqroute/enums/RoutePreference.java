package com.resqroute.enums;

/**
 * The objective function used by Dijkstra when scoring an edge of the road graph.
 */
public enum RoutePreference {
    /** Minimises travel time: distance / (base speed * congestion factor). */
    FASTEST,
    /** Minimises pure distance. */
    SHORTEST,
    /** Minimises distance multiplied by a congestion penalty. */
    LOW_TRAFFIC
}
