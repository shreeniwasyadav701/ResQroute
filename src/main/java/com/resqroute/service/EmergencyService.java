package com.resqroute.service;

import com.resqroute.dto.ResourceDtos.DispatchResponse;
import com.resqroute.dto.ResourceDtos.UnitEtaResponse;
import com.resqroute.dto.ResourceDtos.UnitResponse;
import com.resqroute.entity.EmergencyUnit;
import com.resqroute.entity.Incident;
import com.resqroute.enums.IncidentType;
import com.resqroute.enums.RoutePreference;
import com.resqroute.enums.UnitStatus;
import com.resqroute.enums.UnitType;
import com.resqroute.exception.ApiException;
import com.resqroute.graph.Dijkstra;
import com.resqroute.graph.GraphBuilder;
import com.resqroute.graph.RoadGraph;
import com.resqroute.entity.Road;
import com.resqroute.repository.EmergencyUnitRepository;
import com.resqroute.repository.RoadRepository;
import com.resqroute.websocket.EventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Emergency dispatch: ETA from every eligible unit via Dijkstra, pick the
 * fastest suitable one, assign it, and expose the emergency route.
 */
@Service
public class EmergencyService {

    private final EmergencyUnitRepository units;
    private final IncidentService incidentService;
    private final RoutingService routingService;
    private final EventBroadcaster broadcaster;
    private final GraphBuilder graphBuilder;
    private final RoadRepository roads;

    public EmergencyService(EmergencyUnitRepository units, IncidentService incidentService,
                            RoutingService routingService, EventBroadcaster broadcaster,
                            GraphBuilder graphBuilder, RoadRepository roads) {
        this.units = units;
        this.incidentService = incidentService;
        this.routingService = routingService;
        this.broadcaster = broadcaster;
        this.graphBuilder = graphBuilder;
        this.roads = roads;
    }

    @Transactional(readOnly = true)
    public List<UnitEtaResponse> etasForIncident(Long incidentId, UnitType preferredType) {
        Incident incident = incidentService.require(incidentId);
        List<UnitType> recommended = recommendedTypes(incident.getType());

        List<UnitEtaResponse> etas = new ArrayList<>();
        for (EmergencyUnit unit : units.findAll()) {
            boolean statusEligible = unit.getStatus() == UnitStatus.AVAILABLE;
            boolean typeEligible = preferredType != null
                    ? unit.getType() == preferredType
                    : recommended.contains(unit.getType());
            boolean eligible = statusEligible && typeEligible;

            double eta = 0;
            double distance = 0;
            if (statusEligible) {
                Dijkstra.Path path = routingService.rawPath(
                        unit.getCurrentLocation().getId(),
                        incident.getLocation().getId(),
                        RoutePreference.FASTEST);
                if (path.found()) {
                    eta = path.timeMinutes();
                    distance = path.distanceKm();
                } else {
                    eligible = false;
                }
            }
            etas.add(new UnitEtaResponse(unit.getId(), unit.getName(), unit.getType(),
                    unit.getStatus(), eligible, round(eta), round(distance)));
        }
        etas.sort(Comparator.comparingDouble(UnitEtaResponse::etaMinutes));
        return etas;
    }

    @Transactional
    public DispatchResponse dispatch(Long incidentId, Long unitId) {
        Incident incident = incidentService.require(incidentId);
        if (incident.getStatus() != com.resqroute.enums.IncidentStatus.ACTIVE) {
            throw ApiException.badRequest("Incident is already resolved");
        }

        EmergencyUnit unit = unitId != null ? requireUnit(unitId) : pickFastestUnit(incident);
        if (unit.getStatus() != UnitStatus.AVAILABLE) {
            throw ApiException.conflict("Unit " + unit.getName() + " is not available");
        }

        Dijkstra.Path path = routingService.rawPath(
                unit.getCurrentLocation().getId(),
                incident.getLocation().getId(),
                RoutePreference.FASTEST);
        if (!path.found()) {
            throw ApiException.badRequest("No emergency route from " + unit.getName()
                    + " to the incident - network is disconnected");
        }

        unit.setStatus(UnitStatus.BUSY);
        units.save(unit);
        incident.setAssignedUnit(unit);

        RoadGraph graph = graphBuilder.graph();
        List<String> nodeNames = path.nodes().stream().map(graph::nodeName).toList();
        Map<Long, String> roadNames = roads.findAllById(path.roadIds()).stream()
                .collect(java.util.stream.Collectors.toMap(Road::getId, Road::getName, (a, b) -> a));

        broadcaster.unit(Map.of("unitId", unit.getId(), "status", unit.getStatus().name(),
                "assignedToIncident", incident.getId()));
        broadcaster.incident(incidentService.toResponse(incident));

        return new DispatchResponse(
                incident.getId(),
                unit.getId(),
                unit.getName(),
                unit.getType(),
                round(path.timeMinutes()),
                round(path.distanceKm()),
                nodeNames,
                path.roadIds().stream().map(id -> roadNames.getOrDefault(id, "Road #" + id)).toList(),
                unit.getName() + " dispatched, ETA " + Math.ceil(path.timeMinutes()) + " min");
    }

    @Transactional
    public UnitResponse setStatus(Long unitId, UnitStatus status) {
        EmergencyUnit unit = requireUnit(unitId);
        unit.setStatus(status);
        units.save(unit);
        broadcaster.unit(Map.of("unitId", unit.getId(), "status", status.name()));
        return EmergencyDtos.toUnitResponse(unit);
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits() {
        return units.findAll().stream().map(EmergencyDtos::toUnitResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UnitEtaResponse> allEtas() {
        return units.findAll().stream()
                .map(u -> new UnitEtaResponse(u.getId(), u.getName(), u.getType(), u.getStatus(),
                        u.getStatus() == UnitStatus.AVAILABLE, 0, 0))
                .toList();
    }

    private EmergencyUnit pickFastestUnit(Incident incident) {
        List<UnitType> recommended = recommendedTypes(incident.getType());
        List<EmergencyUnit> available = units.findByStatus(UnitStatus.AVAILABLE);
        if (available.isEmpty()) {
            throw ApiException.conflict("No available emergency units");
        }

        EmergencyUnit best = null;
        double bestEta = Double.POSITIVE_INFINITY;
        EmergencyUnit bestAny = null;
        double bestAnyEta = Double.POSITIVE_INFINITY;

        for (EmergencyUnit unit : available) {
            Dijkstra.Path path = routingService.rawPath(
                    unit.getCurrentLocation().getId(),
                    incident.getLocation().getId(),
                    RoutePreference.FASTEST);
            if (!path.found()) {
                continue;
            }
            if (path.timeMinutes() < bestAnyEta) {
                bestAnyEta = path.timeMinutes();
                bestAny = unit;
            }
            if (recommended.contains(unit.getType()) && path.timeMinutes() < bestEta) {
                bestEta = path.timeMinutes();
                best = unit;
            }
        }

        EmergencyUnit chosen = best != null ? best : bestAny;
        if (chosen == null) {
            throw ApiException.badRequest("No reachable unit for this incident");
        }
        return chosen;
    }

    private EmergencyUnit requireUnit(Long unitId) {
        return units.findById(unitId)
                .orElseThrow(() -> ApiException.notFound("Emergency unit " + unitId + " not found"));
    }

    public static List<UnitType> recommendedTypes(IncidentType type) {
        return switch (type) {
            case FIRE -> List.of(UnitType.FIRE_TRUCK, UnitType.POLICE);
            case ACCIDENT -> List.of(UnitType.AMBULANCE, UnitType.POLICE, UnitType.FIRE_TRUCK);
            case FLOOD, ROAD_BLOCKAGE, TRAFFIC_JAM, BREAKDOWN -> List.of(UnitType.POLICE);
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Small mapper kept next to dispatch logic to avoid a cycle on NetworkService. */
    static final class EmergencyDtos {
        private EmergencyDtos() {
        }

        static UnitResponse toUnitResponse(EmergencyUnit unit) {
            return new UnitResponse(
                    unit.getId(), unit.getName(), unit.getType(), unit.getStatus(),
                    unit.getStation().getId(), unit.getStation().getName(),
                    unit.getCurrentLocation().getId(), unit.getCurrentLocation().getName(),
                    unit.getPhone());
        }
    }
}
