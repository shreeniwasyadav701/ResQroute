package com.resqroute.service;

import com.resqroute.dto.ResourceDtos.DashboardResponse;
import com.resqroute.entity.EmergencyUnit;
import com.resqroute.entity.Hospital;
import com.resqroute.entity.Road;
import com.resqroute.enums.IncidentStatus;
import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.UnitStatus;
import com.resqroute.repository.EmergencyUnitRepository;
import com.resqroute.repository.HospitalRepository;
import com.resqroute.repository.IncidentRepository;
import com.resqroute.repository.RoadRepository;
import com.resqroute.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final IncidentRepository incidents;
    private final EmergencyUnitRepository units;
    private final RoadRepository roads;
    private final HospitalRepository hospitals;
    private final UserRepository users;
    private final IncidentService incidentService;

    public DashboardService(IncidentRepository incidents, EmergencyUnitRepository units,
                            RoadRepository roads, HospitalRepository hospitals,
                            UserRepository users, IncidentService incidentService) {
        this.incidents = incidents;
        this.units = units;
        this.roads = roads;
        this.hospitals = hospitals;
        this.users = users;
        this.incidentService = incidentService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        long openRoads = roads.findAll().stream().filter(r -> r.getStatus() == RoadStatus.OPEN).count();
        long blocked = roads.count() - openRoads;
        long beds = hospitals.findAll().stream().mapToInt(Hospital::getBedsAvailable).sum();
        List<com.resqroute.dto.IncidentDtos.IncidentResponse> recent =
                incidents.findTop5ByOrderByCreatedAtDesc().stream()
                        .map(incidentService::toResponse)
                        .toList();

        return new DashboardResponse(
                incidents.countByStatus(IncidentStatus.ACTIVE),
                incidents.countByStatus(IncidentStatus.RESOLVED),
                units.countByStatus(UnitStatus.AVAILABLE),
                units.countByStatus(UnitStatus.BUSY),
                blocked,
                openRoads,
                beds,
                users.count(),
                recent);
    }

    @Transactional(readOnly = true)
    public List<Road> allRoads() {
        return roads.findAll();
    }

    @Transactional(readOnly = true)
    public long availableUnitCount() {
        return units.countByStatus(UnitStatus.AVAILABLE);
    }
}
