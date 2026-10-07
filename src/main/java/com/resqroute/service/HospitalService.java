package com.resqroute.service;

import com.resqroute.dto.ResourceDtos.HospitalListResponse;
import com.resqroute.dto.ResourceDtos.HospitalRecommendation;
import com.resqroute.dto.ResourceDtos.HospitalRecommendationResponse;
import com.resqroute.dto.ResourceDtos.HospitalRequest;
import com.resqroute.dto.ResourceDtos.HospitalResponse;
import com.resqroute.entity.Hospital;
import com.resqroute.entity.Incident;
import com.resqroute.entity.Intersection;
import com.resqroute.enums.IncidentType;
import com.resqroute.enums.RoutePreference;
import com.resqroute.exception.ApiException;
import com.resqroute.graph.Dijkstra;
import com.resqroute.repository.HospitalRepository;
import com.resqroute.websocket.EventBroadcaster;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hospital lookup: rank facilities by ambulance ETA from the incident node,
 * filtered by capacity and trauma readiness.
 */
@Service
public class HospitalService {

    private final HospitalRepository hospitals;
    private final NetworkService networkService;
    private final IncidentService incidentService;
    private final RoutingService routingService;

    public HospitalService(HospitalRepository hospitals, NetworkService networkService,
                           IncidentService incidentService, RoutingService routingService) {
        this.hospitals = hospitals;
        this.networkService = networkService;
        this.incidentService = incidentService;
        this.routingService = routingService;
    }

    @Transactional
    public HospitalResponse create(HospitalRequest request) {
        Intersection location = networkService.requireIntersection(request.locationId());
        Hospital hospital = new Hospital(request.name(), location,
                request.bedsTotal(), request.bedsAvailable(), request.traumaCenter());
        return toResponse(hospitals.save(hospital));
    }

    @Transactional(readOnly = true)
    public HospitalListResponse list() {
        return new HospitalListResponse(hospitals.findAllByOrderByBedsAvailableDesc().stream()
                .map(HospitalService::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public HospitalResponse updateBeds(Long id, int bedsAvailable) {
        Hospital hospital = require(id);
        hospital.setBedsAvailable(Math.max(0, Math.min(hospital.getBedsTotal(), bedsAvailable)));
        return toResponse(hospitals.save(hospital));
    }

    /**
     * Ranks hospitals for an incident (or an arbitrary node) by ETA.
     * Trauma centres win ties; facilities without free beds are dropped.
     */
    @Transactional(readOnly = true)
    public HospitalRecommendationResponse recommend(Long incidentId, Long fromNodeId,
                                                    Integer minBeds, int limit) {
        Long originId;
        String originName;
        IncidentType incidentType = null;

        if (incidentId != null) {
            Incident incident = incidentService.require(incidentId);
            originId = incident.getLocation().getId();
            originName = incident.getLocation().getName();
            incidentType = incident.getType();
        } else {
            if (fromNodeId == null) {
                throw ApiException.badRequest("Provide incidentId or fromNodeId");
            }
            Intersection node = networkService.requireIntersection(fromNodeId);
            originId = node.getId();
            originName = node.getName();
        }

        int bedsRequired = minBeds == null ? 1 : Math.max(1, minBeds);
        boolean traumaPreferred = incidentType == IncidentType.ACCIDENT;

        List<HospitalRecommendation> scored = new ArrayList<>();
        for (Hospital hospital : hospitals.findAll()) {
            if (hospital.getBedsAvailable() < bedsRequired || !hospital.isEmergencyReady()) {
                continue;
            }
            Dijkstra.Path path = routingService.rawPath(originId, hospital.getLocation().getId(),
                    RoutePreference.FASTEST);
            if (!path.found()) {
                continue;
            }
            scored.add(new HospitalRecommendation(
                    toResponse(hospital),
                    round(path.timeMinutes()),
                    round(path.distanceKm()),
                    0,
                    reason(hospital, path.timeMinutes(), traumaPreferred)));
        }

        scored.sort(Comparator
                .comparingDouble(HospitalRecommendation::etaMinutes)
                .thenComparing(Comparator.comparingInt(
                        (HospitalRecommendation r) -> r.hospital().bedsAvailable()).reversed()));

        List<HospitalRecommendation> ranked = new ArrayList<>();
        int rank = 1;
        for (HospitalRecommendation candidate : scored) {
            if (rank > limit) {
                break;
            }
            ranked.add(new HospitalRecommendation(candidate.hospital(), candidate.etaMinutes(),
                    candidate.distanceKm(), rank, candidate.reason()));
            rank++;
        }

        if (ranked.isEmpty()) {
            throw ApiException.notFound("No hospital reachable with the required capacity");
        }
        return new HospitalRecommendationResponse(incidentId, originId, originName, ranked);
    }

    private Hospital require(Long id) {
        return hospitals.findById(id)
                .orElseThrow(() -> ApiException.notFound("Hospital " + id + " not found"));
    }

    private static String reason(Hospital hospital, double eta, boolean traumaPreferred) {
        StringBuilder sb = new StringBuilder();
        sb.append("ETA ").append(Math.ceil(eta)).append(" min, ")
                .append(hospital.getBedsAvailable()).append(" beds free");
        if (hospital.isTraumaCenter()) {
            sb.append(traumaPreferred ? ", trauma centre (priority)" : ", trauma centre");
        }
        return sb.toString();
    }

    public static HospitalResponse toResponse(Hospital hospital) {
        return new HospitalResponse(
                hospital.getId(), hospital.getName(),
                hospital.getLocation().getId(), hospital.getLocation().getName(),
                hospital.getBedsTotal(), hospital.getBedsAvailable(),
                hospital.isTraumaCenter(), hospital.isEmergencyReady());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
