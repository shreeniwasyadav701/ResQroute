package com.resqroute.service;

import com.resqroute.dto.IncidentDtos.IncidentCreateRequest;
import com.resqroute.dto.IncidentDtos.IncidentListResponse;
import com.resqroute.dto.IncidentDtos.IncidentResponse;
import com.resqroute.entity.EmergencyUnit;
import com.resqroute.entity.Incident;
import com.resqroute.entity.Intersection;
import com.resqroute.entity.Road;
import com.resqroute.enums.IncidentStatus;
import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.Severity;
import com.resqroute.enums.TrafficLevel;
import com.resqroute.enums.UnitStatus;
import com.resqroute.exception.ApiException;
import com.resqroute.repository.EmergencyUnitRepository;
import com.resqroute.repository.IncidentRepository;
import com.resqroute.websocket.EventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Incident lifecycle: report -> mark affected road -> degrade traffic ->
 * rebuild graph -> notify dashboards. Resolve reverses all of it.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidents;
    private final EmergencyUnitRepository units;
    private final NetworkService networkService;
    private final RoutingService routingService;
    private final EventBroadcaster broadcaster;

    public IncidentService(IncidentRepository incidents, EmergencyUnitRepository units,
                           NetworkService networkService, RoutingService routingService,
                           EventBroadcaster broadcaster) {
        this.incidents = incidents;
        this.units = units;
        this.networkService = networkService;
        this.routingService = routingService;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public IncidentResponse report(IncidentCreateRequest request, String username) {
        Intersection location = networkService.requireIntersection(request.locationId());

        Road affectedRoad = request.affectedRoadId() != null
                ? networkService.requireRoad(request.affectedRoadId())
                : pickAffectedRoad(location);

        Incident incident = new Incident();
        incident.setType(request.type());
        incident.setSeverity(request.severity());
        incident.setDescription(request.description());
        incident.setLocation(location);
        incident.setAffectedRoad(affectedRoad);
        incident.setReportedBy(username);
        incident.setStatus(IncidentStatus.ACTIVE);

        applySeverityToRoad(affectedRoad, request.severity());
        Incident saved = incidents.save(incident);

        routingService.recomputeStoredRoutes();

        IncidentResponse response = toResponse(saved);
        broadcaster.incident(response);
        if (affectedRoad != null) {
            broadcaster.traffic(Map.of(
                    "roadId", affectedRoad.getId(),
                    "roadName", affectedRoad.getName(),
                    "trafficLevel", affectedRoad.getTrafficLevel().name(),
                    "status", affectedRoad.getStatus().name(),
                    "reason", "Incident #" + saved.getId()));
        }
        return response;
    }

    @Transactional
    public IncidentResponse resolve(Long incidentId) {
        Incident incident = require(incidentId);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw ApiException.conflict("Incident " + incidentId + " is already resolved");
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());

        Road road = incident.getAffectedRoad();
        if (road != null) {
            road.setStatus(RoadStatus.OPEN);
            road.setTrafficLevel(TrafficLevel.FREE_FLOW);
        }

        EmergencyUnit unit = incident.getAssignedUnit();
        if (unit != null) {
            unit.setStatus(UnitStatus.AVAILABLE);
            unit.setCurrentLocation(incident.getLocation());
            units.save(unit);
            broadcaster.unit(Map.of("unitId", unit.getId(), "status", unit.getStatus().name(),
                    "reason", "Incident resolved"));
        }
        incident.setAssignedUnit(null);

        Incident saved = incidents.save(incident);
        routingService.recomputeStoredRoutes();

        IncidentResponse response = toResponse(saved);
        broadcaster.incident(response);
        if (road != null) {
            broadcaster.traffic(Map.of(
                    "roadId", road.getId(),
                    "roadName", road.getName(),
                    "trafficLevel", road.getTrafficLevel().name(),
                    "status", road.getStatus().name(),
                    "reason", "Incident #" + saved.getId() + " resolved"));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Incident require(Long id) {
        return incidents.findById(id)
                .orElseThrow(() -> ApiException.notFound("Incident " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public IncidentListResponse list(IncidentStatus status) {
        List<Incident> source = status == null
                ? incidents.findAllByOrderByCreatedAtDesc()
                : incidents.findByStatusOrderByCreatedAtDesc(status);
        return new IncidentListResponse(source.stream().map(this::toResponse).toList());
    }

    /**
     * Severity drives the road state:
     * CRITICAL -> road blocked, HIGH -> gridlock, MEDIUM -> heavy, LOW -> moderate.
     */
    private void applySeverityToRoad(Road road, Severity severity) {
        if (road == null) {
            return;
        }
        switch (severity) {
            case CRITICAL -> {
                road.setStatus(RoadStatus.BLOCKED);
                road.setTrafficLevel(TrafficLevel.GRIDLOCK);
            }
            case HIGH -> road.setTrafficLevel(TrafficLevel.GRIDLOCK);
            case MEDIUM -> road.setTrafficLevel(TrafficLevel.HEAVY);
            case LOW -> road.setTrafficLevel(TrafficLevel.MODERATE);
        }
        networkService.refreshGraph();
    }

    /**
     * When the reporter does not name a road, pick the most congested road that
     * touches the reported intersection.
     */
    private Road pickAffectedRoad(Intersection location) {
        List<Road> touching = networkService.roadsTouching(location);
        if (touching.isEmpty()) {
            return null;
        }
        return touching.stream()
                .max(Comparator.comparingInt(r -> r.getTrafficLevel().ordinal()))
                .orElse(touching.get(0));
    }

    public IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getType(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getDescription(),
                incident.getLocation().getId(),
                incident.getLocation().getName(),
                incident.getAffectedRoad() != null ? incident.getAffectedRoad().getId() : null,
                incident.getAffectedRoad() != null ? incident.getAffectedRoad().getName() : null,
                incident.getReportedBy(),
                incident.getAssignedUnit() != null ? incident.getAssignedUnit().getId() : null,
                incident.getAssignedUnit() != null ? incident.getAssignedUnit().getName() : null,
                incident.getCreatedAt(),
                incident.getResolvedAt());
    }
}
