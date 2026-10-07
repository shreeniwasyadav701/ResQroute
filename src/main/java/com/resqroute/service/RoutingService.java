package com.resqroute.service;

import com.resqroute.dto.RouteDtos.RouteHistoryResponse;
import com.resqroute.dto.RouteDtos.RoutePlanResponse;
import com.resqroute.dto.RouteDtos.RouteQuery;
import com.resqroute.dto.RouteDtos.RouteRequestResponse;
import com.resqroute.dto.RouteDtos.RouteResultDto;
import com.resqroute.entity.Intersection;
import com.resqroute.entity.Road;
import com.resqroute.entity.RouteRequest;
import com.resqroute.enums.RoutePreference;
import com.resqroute.exception.ApiException;
import com.resqroute.graph.Dijkstra;
import com.resqroute.graph.GraphBuilder;
import com.resqroute.graph.RoadGraph;
import com.resqroute.repository.RoadRepository;
import com.resqroute.repository.RouteRequestRepository;
import com.resqroute.websocket.EventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Route calculation: runs Dijkstra three times, once per objective function,
 * and returns Fastest / Shortest / Low-Traffic options side by side.
 */
@Service
public class RoutingService {

    private final NetworkService networkService;
    private final GraphBuilder graphBuilder;
    private final RoadRepository roads;
    private final RouteRequestRepository routeRequests;
    private final EventBroadcaster broadcaster;

    public RoutingService(NetworkService networkService, GraphBuilder graphBuilder,
                          RoadRepository roads, RouteRequestRepository routeRequests,
                          EventBroadcaster broadcaster) {
        this.networkService = networkService;
        this.graphBuilder = graphBuilder;
        this.roads = roads;
        this.routeRequests = routeRequests;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public RoutePlanResponse computeRoutes(RouteQuery query, String username) {
        if (query.sourceId().equals(query.destinationId())) {
            throw ApiException.badRequest("Source and destination must differ");
        }
        Intersection source = networkService.requireIntersection(query.sourceId());
        Intersection destination = networkService.requireIntersection(query.destinationId());
        RoadGraph graph = graphBuilder.graph();

        Map<RoutePreference, RouteResultDto> results = new EnumMap<>(RoutePreference.class);
        for (RoutePreference preference : RoutePreference.values()) {
            results.put(preference, solve(graph, source, destination, preference));
        }

        List<RouteResultDto> ordered = new ArrayList<>();
        if (query.preference() != null) {
            ordered.add(results.get(query.preference()));
            for (RoutePreference preference : RoutePreference.values()) {
                if (preference != query.preference()) {
                    ordered.add(results.get(preference));
                }
            }
        } else {
            ordered.addAll(results.values());
        }

        RouteResultDto primary = ordered.get(0);
        RouteRequest request = new RouteRequest();
        request.setSource(source);
        request.setDestination(destination);
        request.setPreference(primary.preference());
        request.setRequestedBy(username);
        request.setPathSummary(primary.summary());
        request.setDistanceKm(primary.distanceKm());
        request.setTimeMinutes(primary.timeMinutes());
        request.setAffectedRoadIds(primary.roadIds().stream().map(String::valueOf)
                .collect(Collectors.joining(",")));
        RouteRequest saved = routeRequests.save(request);

        return new RoutePlanResponse(saved.getId(), source.getName(), destination.getName(), ordered);
    }

    @Transactional(readOnly = true)
    public RouteResultDto solve(RoadGraph graph, Intersection source, Intersection destination,
                                RoutePreference preference) {
        Dijkstra.Path path = Dijkstra.findPath(
                graph.adjacency(), source.getId(), destination.getId(),
                GraphBuilder.weightFunction(preference));

        if (!path.found()) {
            return RouteResultDto.notFound(preference);
        }

        Map<Long, String> roadNames = roads.findAllById(path.roadIds()).stream()
                .collect(Collectors.toMap(Road::getId, Road::getName, (a, b) -> a));

        List<String> nodeNames = path.nodes().stream().map(graph::nodeName).toList();
        List<String> roadNamesInOrder = path.roadIds().stream()
                .map(id -> roadNames.getOrDefault(id, "Road #" + id))
                .toList();

        double avgSpeed = path.timeMinutes() > 0 ? path.distanceKm() / (path.timeMinutes() / 60.0) : 0;
        String summary = String.format("%.1f km | %.0f min | %d segments",
                path.distanceKm(), Math.ceil(path.timeMinutes()), path.roadIds().size());

        return new RouteResultDto(preference, true, path.nodes(), nodeNames,
                path.roadIds(), roadNamesInOrder, round(path.distanceKm()),
                round(path.timeMinutes()), round(path.cost()), round(avgSpeed), summary);
    }

    /**
     * Recomputes every stored route against the current graph. Called after the
     * road network changes so the dashboard can flag invalidated routes.
     */
    @Transactional
    public int recomputeStoredRoutes() {
        RoadGraph graph = graphBuilder.graph();
        int touched = 0;
        for (RouteRequest request : routeRequests.findAll()) {
            Dijkstra.Path path = Dijkstra.findPath(
                    graph.adjacency(), request.getSource().getId(), request.getDestination().getId(),
                    GraphBuilder.weightFunction(request.getPreference()));
            request.setPathSummary(path.found()
                    ? String.format("%.1f km | %.0f min | %d segments",
                    path.distanceKm(), Math.ceil(path.timeMinutes()), path.roadIds().size())
                    : "No traversable path - blocked network");
            request.setDistanceKm(path.found() ? round(path.distanceKm()) : null);
            request.setTimeMinutes(path.found() ? round(path.timeMinutes()) : null);
            routeRequests.save(request);
            touched++;
        }
        if (touched > 0) {
            broadcaster.routeRefresh(Map.of("recomputed", touched));
        }
        return touched;
    }

    @Transactional(readOnly = true)
    public RouteHistoryResponse history(int limit) {
        List<RouteRequestResponse> list = routeRequests.findAllByOrderByCreatedAtDesc().stream()
                .limit(limit)
                .map(r -> new RouteRequestResponse(
                        r.getId(), r.getSource().getId(), r.getSource().getName(),
                        r.getDestination().getId(), r.getDestination().getName(),
                        r.getPreference(), r.getPathSummary(), r.getDistanceKm(),
                        r.getTimeMinutes(), r.getCreatedAt(), r.getRequestedBy()))
                .toList();
        return new RouteHistoryResponse(list);
    }

    @Transactional(readOnly = true)
    public Dijkstra.Path rawPath(Long sourceId, Long destinationId, RoutePreference preference) {
        RoadGraph graph = graphBuilder.graph();
        return Dijkstra.findPath(graph.adjacency(), sourceId, destinationId,
                GraphBuilder.weightFunction(preference == null ? RoutePreference.FASTEST : preference));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
