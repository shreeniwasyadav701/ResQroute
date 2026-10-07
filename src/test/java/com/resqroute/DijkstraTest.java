package com.resqroute;

import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.TrafficLevel;
import com.resqroute.graph.Dijkstra;
import com.resqroute.graph.GraphBuilder;
import com.resqroute.graph.RoadGraph;
import com.resqroute.enums.RoutePreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dijkstra unit tests on a hand-built graph:
 *
 *          (10km, 100kph, free)        (5km, 100kph)         (4km, 100kph)
 *   [1] -------------------------------- [2] ------------------- [4] ------ [5]
 *     \                                  /
 *      \ (6km, 40kph, HEAVY)           / (5km, 100kph, free)
 *       ----------------------------- [3]
 *
 *   Plus a shortcut [1]-[5] of 2km that is CLOSED and must never be used.
 */
class DijkstraTest {

    private RoadGraph graph;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        for (long id = 1; id <= 5; id++) {
            graph.addNode(id, "N" + id);
        }
        graph.addBidirectional(edge(12, 1, 2, 10, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        graph.addBidirectional(edge(13, 1, 3, 6, 40, TrafficLevel.HEAVY, RoadStatus.OPEN));
        graph.addBidirectional(edge(24, 2, 4, 5, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        graph.addBidirectional(edge(34, 3, 4, 5, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        graph.addBidirectional(edge(45, 4, 5, 4, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        graph.addBidirectional(edge(15, 1, 5, 2, 100, TrafficLevel.FREE_FLOW, RoadStatus.CLOSED));
    }

    private static RoadGraph.Edge edge(long roadId, long from, long to, double km,
                                       double speed, TrafficLevel level, RoadStatus status) {
        return new RoadGraph.Edge(roadId, from, to, km, speed, level, status);
    }

    @Test
    @DisplayName("SHORTEST minimises distance: 1 -> 3 -> 4 -> 5 (15 km)")
    void shortestPrefersFewerKilometres() {
        Dijkstra.Path path = Dijkstra.findPath(graph.adjacency(), 1, 5,
                GraphBuilder.weightFunction(RoutePreference.SHORTEST));

        assertTrue(path.found());
        assertEquals(15.0, path.distanceKm(), 0.0001);
        assertEquals(java.util.List.of(1L, 3L, 4L, 5L), path.nodes());
        assertEquals(java.util.List.of(13L, 34L, 45L), path.roadIds());
    }

    @Test
    @DisplayName("FASTEST minimises time: 1 -> 2 -> 4 -> 5 (11.4 min) despite longer distance")
    void fastestIgnoresDistance() {
        Dijkstra.Path path = Dijkstra.findPath(graph.adjacency(), 1, 5,
                GraphBuilder.weightFunction(RoutePreference.FASTEST));

        assertTrue(path.found());
        assertEquals(java.util.List.of(1L, 2L, 4L, 5L), path.nodes());
        assertEquals(19.0, path.distanceKm(), 0.0001);
        // 10/100 + 5/100 + 4/100 h = 0.19 h = 11.4 min
        assertEquals(11.4, path.timeMinutes(), 0.05);
    }

    @Test
    @DisplayName("LOW_TRAFFIC avoids the HEAVY segment even when it is shorter")
    void lowTrafficAvoidsCongestion() {
        Dijkstra.Path path = Dijkstra.findPath(graph.adjacency(), 1, 5,
                GraphBuilder.weightFunction(RoutePreference.LOW_TRAFFIC));

        assertTrue(path.found());
        assertEquals(java.util.List.of(1L, 2L, 4L, 5L), path.nodes());
        // 1-3 costs 6 * (1 + 1.5) = 15 > 10 for 1-2
    }

    @Test
    @DisplayName("A BLOCKED road is never traversed, routes reroute automatically")
    void blockedRoadIsSkipped() {
        RoadGraph blocked = new RoadGraph();
        for (long id = 1; id <= 5; id++) {
            blocked.addNode(id, "N" + id);
        }
        blocked.addBidirectional(edge(12, 1, 2, 10, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        blocked.addBidirectional(edge(13, 1, 3, 6, 40, TrafficLevel.HEAVY, RoadStatus.OPEN));
        blocked.addBidirectional(edge(24, 2, 4, 5, 100, TrafficLevel.FREE_FLOW, RoadStatus.BLOCKED));
        blocked.addBidirectional(edge(34, 3, 4, 5, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));
        blocked.addBidirectional(edge(45, 4, 5, 4, 100, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));

        Dijkstra.Path path = Dijkstra.findPath(blocked.adjacency(), 1, 5,
                GraphBuilder.weightFunction(RoutePreference.FASTEST));

        assertTrue(path.found());
        assertFalse(path.roadIds().contains(24L));
        assertEquals(java.util.List.of(1L, 3L, 4L, 5L), path.nodes());
    }

    @Test
    @DisplayName("Disjoint target returns notFound instead of hanging")
    void unreachableTargetReturnsNotFound() {
        graph.addNode(99, "ISOLATED");

        Dijkstra.Path path = Dijkstra.findPath(graph.adjacency(), 1, 99,
                GraphBuilder.weightFunction(RoutePreference.FASTEST));

        assertFalse(path.found());
        assertTrue(path.nodes().isEmpty());
    }

    @Test
    @DisplayName("Gridlock raises travel time through the segment")
    void congestionIncreasesTravelTime() {
        RoadGraph free = new RoadGraph();
        free.addNode(1, "A");
        free.addNode(2, "B");
        free.addBidirectional(edge(1, 1, 2, 10, 50, TrafficLevel.FREE_FLOW, RoadStatus.OPEN));

        RoadGraph jammed = new RoadGraph();
        jammed.addNode(1, "A");
        jammed.addNode(2, "B");
        jammed.addBidirectional(edge(1, 1, 2, 10, 50, TrafficLevel.GRIDLOCK, RoadStatus.OPEN));

        double freeMinutes = Dijkstra.findPath(free.adjacency(), 1, 2,
                GraphBuilder.weightFunction(RoutePreference.FASTEST)).timeMinutes();
        double jamMinutes = Dijkstra.findPath(jammed.adjacency(), 1, 2,
                GraphBuilder.weightFunction(RoutePreference.FASTEST)).timeMinutes();

        assertTrue(jamMinutes > freeMinutes, "gridlock must be slower");
        assertEquals(12.0, freeMinutes, 0.01);
        assertEquals(48.0, jamMinutes, 0.01);
    }
}
