package com.resqroute.controller;

import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.dto.ResourceDtos.DispatchRequest;
import com.resqroute.dto.ResourceDtos.UnitListResponse;
import com.resqroute.dto.ResourceDtos.UnitRequest;
import com.resqroute.dto.ResourceDtos.UnitResponse;
import com.resqroute.dto.ResourceDtos.UnitEtaResponse;
import com.resqroute.entity.EmergencyUnit;
import com.resqroute.enums.UnitStatus;
import com.resqroute.enums.UnitType;
import com.resqroute.exception.ApiException;
import com.resqroute.repository.EmergencyUnitRepository;
import com.resqroute.service.EmergencyService;
import com.resqroute.service.NetworkService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {

    private final EmergencyService emergencyService;
    private final EmergencyUnitRepository units;
    private final NetworkService networkService;

    public EmergencyController(EmergencyService emergencyService,
                               EmergencyUnitRepository units,
                               NetworkService networkService) {
        this.emergencyService = emergencyService;
        this.units = units;
        this.networkService = networkService;
    }

    @GetMapping("/units")
    public ApiResponse units() {
        return ApiResponse.ok("Emergency units", new UnitListResponse(emergencyService.listUnits()));
    }

    @PostMapping("/units")
    @PreAuthorize("hasAnyRole('ADMIN','EMERGENCY_OPERATOR')")
    public ApiResponse createUnit(@Valid @RequestBody UnitRequest request) {
        var station = networkService.requireIntersection(request.stationNodeId());
        EmergencyUnit unit = new EmergencyUnit(request.name(), request.type(), station, request.phone());
        UnitResponse saved = toResponse(units.save(unit));
        return ApiResponse.ok("Unit created", saved);
    }

    @PatchMapping("/units/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMERGENCY_OPERATOR')")
    public ApiResponse setStatus(@PathVariable Long id, @RequestParam UnitStatus status) {
        return ApiResponse.ok("Unit status updated", emergencyService.setStatus(id, status));
    }

    /** ETA of every unit for a given incident, sorted ascending. */
    @GetMapping("/etas")
    @PreAuthorize("hasAnyRole('ADMIN','EMERGENCY_OPERATOR','TRAFFIC_OPERATOR')")
    public ApiResponse etas(@RequestParam Long incidentId,
                            @RequestParam(required = false) UnitType type) {
        return ApiResponse.ok("Unit ETAs", emergencyService.etasForIncident(incidentId, type));
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN','EMERGENCY_OPERATOR')")
    public ApiResponse dispatch(@Valid @RequestBody DispatchRequest request) {
        Long unitId = request.unitId() != null ? request.unitId() : null;
        return ApiResponse.ok("Unit dispatched", emergencyService.dispatch(request.incidentId(), unitId));
    }

    static UnitResponse toResponse(EmergencyUnit unit) {
        return new UnitResponse(
                unit.getId(), unit.getName(), unit.getType(), unit.getStatus(),
                unit.getStation().getId(), unit.getStation().getName(),
                unit.getCurrentLocation().getId(), unit.getCurrentLocation().getName(),
                unit.getPhone());
    }
}
