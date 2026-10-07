package com.resqroute.service;

import com.resqroute.dto.ResourceDtos.IntersectionRequest;
import com.resqroute.dto.ResourceDtos.IntersectionResponse;
import com.resqroute.dto.ResourceDtos.RoadListResponse;
import com.resqroute.dto.ResourceDtos.RoadRequest;
import com.resqroute.dto.ResourceDtos.RoadResponse;
import com.resqroute.dto.IncidentDtos.TrafficUpdateResponse;
import com.resqroute.entity.Incident;
import com.resqroute.entity.Intersection;
import com.resqroute.entity.Road;
import com.resqroute.enums.IncidentStatus;
import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.TrafficLevel;
import com.resqroute.exception.ApiException;
import com.resqroute.graph.GraphBuilder;
import com.resqroute.repository.IncidentRepository;
import com.resqroute.repository.IntersectionRepository;
import com.resqroute.repository.RoadRepository;
import com.resqroute.websocket.EventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD over the road network plus the traffic state changes that feed the graph.
 */
@Service
public class NetworkService {

    private final IntersectionRepository intersections;
    private final RoadRepository roads;
    private final IncidentRepository incidents;
    private final GraphBuilder graphBuilder;
    private final EventBroadcaster broadcaster;

    public NetworkService(IntersectionRepository intersections, RoadRepository roads,
                          IncidentRepository incidents, GraphBuilder graphBuilder,
                          EventBroadcaster broadcaster) {
        this.intersections = intersections;
        this.roads = roads;
        this.incidents = incidents;
        this.graphBuilder = graphBuilder;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public IntersectionResponse createIntersection(IntersectionRequest request) {
        Intersection saved = intersections.save(new Intersection(request.name(), request.x(), request.y()));
        graphBuilder.invalidate();
        return toIntersectionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IntersectionResponse> listIntersections() {
        return intersections.findAllByOrderByXAscYAsc().stream().map(NetworkService::toIntersectionResponse).toList();
    }

    @Transactional(readOnly = true)
    public Intersection requireIntersection(Long id) {
        return intersections.findById(id)
                .orElseThrow(() -> ApiException.notFound("Intersection " + id + " not found"));
    }

    @Transactional
    public RoadResponse createRoad(RoadRequest request) {
        Intersection from = requireIntersection(request.fromNodeId());
        Intersection to = requireIntersection(request.toNodeId());
        if (from.getId().equals(to.getId())) {
            throw ApiException.badRequest("A road must connect two different intersections");
        }
        Road road = new Road(request.name(), from, to, request.distanceKm(), request.baseSpeedKmh());
        road.setOneway(request.oneway());
        Road saved = roads.save(road);
        graphBuilder.invalidate();
        return toRoadResponse(saved);
    }

    @Transactional(readOnly = true)
    public RoadResponse updateTraffic(Long roadId, TrafficLevel level, RoadStatus status) {
        Road road = roads.findById(roadId)
                .orElseThrow(() -> ApiException.notFound("Road " + roadId + " not found"));
        if (level != null) {
            road.setTrafficLevel(level);
        }
        if (status != null) {
            road.setStatus(status);
        }
        Road saved = roads.save(road);
        graphBuilder.invalidate();
        TrafficUpdateResponse update = new TrafficUpdateResponse(
                saved.getId(), saved.getName(), saved.getTrafficLevel().name(),
                saved.getStatus().name(), activeIncidentsOnRoad(saved.getId()));
        broadcaster.traffic(update);
        broadcaster.routeRefresh(update);
        return toRoadResponse(saved);
    }

    @Transactional(readOnly = true)
    public Road requireRoad(Long roadId) {
        return roads.findById(roadId)
                .orElseThrow(() -> ApiException.notFound("Road " + roadId + " not found"));
    }

    @Transactional(readOnly = true)
    public RoadListResponse listRoads() {
        return new RoadListResponse(roads.findAll().stream().map(NetworkService::toRoadResponse).toList());
    }

    @Transactional(readOnly = true)
    public List<Road> roadsTouching(Intersection node) {
        return roads.findByFromNodeOrToNode(node, node);
    }

    public void refreshGraph() {
        graphBuilder.rebuild();
    }

    private long activeIncidentsOnRoad(Long roadId) {
        return incidents.findByStatusOrderByCreatedAtDesc(IncidentStatus.ACTIVE).stream()
                .filter(i -> i.getAffectedRoad() != null && roadId.equals(i.getAffectedRoad().getId()))
                .count();
    }

    public static IntersectionResponse toIntersectionResponse(Intersection node) {
        return new IntersectionResponse(node.getId(), node.getName(), node.getX(), node.getY());
    }

    public static RoadResponse toRoadResponse(Road road) {
        return new RoadResponse(
                road.getId(), road.getName(),
                road.getFromNode().getId(), road.getFromNode().getName(),
                road.getToNode().getId(), road.getToNode().getName(),
                road.getDistanceKm(), road.getBaseSpeedKmh(),
                road.getTrafficLevel(), road.getStatus(),
                road.isOneway(), road.travelTimeMinutes());
    }
}
