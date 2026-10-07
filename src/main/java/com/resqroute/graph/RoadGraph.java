package com.resqroute.graph;

import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.TrafficLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Road network as an adjacency list: intersections are nodes, roads are
 * weighted edges. Two-way roads are inserted in both directions.
 *
 * Space complexity: O(V + E).
 */
public final class RoadGraph {

    public record Edge(long roadId, long from, long to, double distanceKm,
                       double baseSpeedKmh, TrafficLevel traffic, RoadStatus status) {
        public boolean traversable() {
            return status == RoadStatus.OPEN;
        }
    }

    private final Map<Long, List<Edge>> adjacency = new LinkedHashMap<>();
    private final Map<Long, String> nodeNames = new LinkedHashMap<>();

    public void addNode(long id, String name) {
        adjacency.computeIfAbsent(id, k -> new ArrayList<>());
        nodeNames.put(id, name);
    }

    public void addEdge(Edge edge) {
        adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        adjacency.computeIfAbsent(edge.to(), k -> new ArrayList<>());
    }

    public void addBidirectional(Edge forward) {
        addEdge(forward);
        addEdge(new Edge(forward.roadId(), forward.to(), forward.from(), forward.distanceKm(),
                forward.baseSpeedKmh(), forward.traffic(), forward.status()));
    }

    public Map<Long, List<Edge>> adjacency() {
        return Collections.unmodifiableMap(adjacency);
    }

    public List<Edge> edgesFrom(long nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    public boolean hasNode(long nodeId) {
        return adjacency.containsKey(nodeId);
    }

    public String nodeName(long nodeId) {
        return nodeNames.getOrDefault(nodeId, String.valueOf(nodeId));
    }

    public int nodeCount() {
        return adjacency.size();
    }

    public int edgeCount() {
        return adjacency.values().stream().mapToInt(List::size).sum();
    }
}
