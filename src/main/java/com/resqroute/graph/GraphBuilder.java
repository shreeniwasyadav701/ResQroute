package com.resqroute.graph;

import com.resqroute.enums.RoutePreference;
import com.resqroute.enums.RoadStatus;
import com.resqroute.repository.RoadRepository;
import com.resqroute.entity.Road;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Builds the in-memory graph from the roads table and keeps it in sync.
 * Weight functions per routing objective live here.
 */
@Component
public class GraphBuilder {

    private final RoadRepository roadRepository;
    private volatile RoadGraph cache;

    public GraphBuilder(RoadRepository roadRepository) {
        this.roadRepository = roadRepository;
    }

    public RoadGraph graph() {
        RoadGraph local = cache;
        if (local == null) {
            local = rebuild();
        }
        return local;
    }

    public synchronized RoadGraph rebuild() {
        RoadGraph graph = new RoadGraph();
        List<Road> roads = roadRepository.findAll();
        for (Road road : roads) {
            graph.addNode(road.getFromNode().getId(), road.getFromNode().getName());
            graph.addNode(road.getToNode().getId(), road.getToNode().getName());
            RoadGraph.Edge edge = new RoadGraph.Edge(
                    road.getId(),
                    road.getFromNode().getId(),
                    road.getToNode().getId(),
                    road.getDistanceKm(),
                    road.getBaseSpeedKmh(),
                    road.getTrafficLevel(),
                    road.getStatus());
            graph.addEdge(edge);
            if (!road.isOneway()) {
                graph.addEdge(new RoadGraph.Edge(edge.roadId(), edge.to(), edge.from(),
                        edge.distanceKm(), edge.baseSpeedKmh(), edge.traffic(), edge.status()));
            }
        }
        this.cache = graph;
        return graph;
    }

    public void invalidate() {
        this.cache = null;
    }

    public static ToDoubleFunction<RoadGraph.Edge> weightFunction(RoutePreference preference) {
        return switch (preference) {
            case SHORTEST -> RoadGraph.Edge::distanceKm;
            case FASTEST -> edge -> edge.distanceKm()
                    / Math.max(5.0, edge.baseSpeedKmh() * edge.traffic().getSpeedFactor());
            case LOW_TRAFFIC -> edge -> edge.distanceKm() * (1.0 + edge.traffic().getCongestionPenalty());
        };
    }

    public static boolean isOpen(Road road) {
        return road.getStatus() == RoadStatus.OPEN;
    }
}
