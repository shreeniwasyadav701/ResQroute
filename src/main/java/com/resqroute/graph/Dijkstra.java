package com.resqroute.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Dijkstra's shortest path over the road graph.
 *
 * Priority queue of (node, accumulated cost); each node is settled once.
 * Time complexity:  O((V + E) log V)
 * Space complexity: O(V + E)
 *
 * The objective function is injected as a weight function, so the same
 * implementation serves FASTEST (time), SHORTEST (distance) and
 * LOW_TRAFFIC (congestion weighted distance) routing.
 */
public final class Dijkstra {

    private Dijkstra() {
    }

    public record Path(boolean found, List<Long> nodes, List<Long> roadIds,
                       double cost, double distanceKm, double timeMinutes) {

        public static Path notFound() {
            return new Path(false, List.of(), List.of(), Double.POSITIVE_INFINITY, 0, 0);
        }
    }

    private record State(long node, double cost) implements Comparable<State> {
        @Override
        public int compareTo(State other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    public static Path findPath(Map<Long, List<RoadGraph.Edge>> adjacency,
                                long source,
                                long target,
                                ToDoubleFunction<RoadGraph.Edge> weightFn) {
        if (!adjacency.containsKey(source) || !adjacency.containsKey(target)) {
            return Path.notFound();
        }

        Map<Long, Double> best = new HashMap<>();
        Map<Long, RoadGraph.Edge> cameFrom = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        Queue<State> frontier = new PriorityQueue<>();

        best.put(source, 0.0);
        frontier.add(new State(source, 0.0));

        while (!frontier.isEmpty()) {
            State current = frontier.poll();
            if (settled.contains(current.node())) {
                continue;
            }
            settled.add(current.node());
            if (current.node() == target) {
                break;
            }

            for (RoadGraph.Edge edge : adjacency.getOrDefault(current.node(), List.of())) {
                if (!edge.traversable()) {
                    continue;
                }
                double candidate = current.cost() + weightFn.applyAsDouble(edge);
                double known = best.getOrDefault(edge.to(), Double.POSITIVE_INFINITY);
                if (candidate + 1e-9 < known) {
                    best.put(edge.to(), candidate);
                    cameFrom.put(edge.to(), edge);
                    frontier.add(new State(edge.to(), candidate));
                }
            }
        }

        if (!settled.contains(target)) {
            return Path.notFound();
        }
        return reconstruct(source, target, best.get(target), cameFrom);
    }

    private static Path reconstruct(long source, long target, double cost,
                                    Map<Long, RoadGraph.Edge> cameFrom) {
        List<Long> nodes = new ArrayList<>();
        List<Long> roadIds = new ArrayList<>();
        double distanceKm = 0;
        double timeMinutes = 0;

        long cursor = target;
        nodes.add(cursor);
        while (cursor != source) {
            RoadGraph.Edge edge = cameFrom.get(cursor);
            if (edge == null) {
                return Path.notFound();
            }
            roadIds.add(edge.roadId());
            distanceKm += edge.distanceKm();
            timeMinutes += edge.distanceKm() / Math.max(5.0, edge.baseSpeedKmh() * edge.traffic().getSpeedFactor()) * 60.0;
            cursor = edge.from();
            nodes.add(cursor);
        }
        Collections.reverse(nodes);
        Collections.reverse(roadIds);
        return new Path(true, nodes, roadIds, cost, distanceKm, timeMinutes);
    }
}
