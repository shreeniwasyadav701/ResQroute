package com.resqroute.controller;

import com.resqroute.dto.IncidentDtos.IncidentCreateRequest;
import com.resqroute.dto.IncidentDtos.IncidentListResponse;
import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.enums.IncidentStatus;
import com.resqroute.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ApiResponse report(@Valid @RequestBody IncidentCreateRequest request,
                              @AuthenticationPrincipal UserDetails principal) {
        String username = principal != null ? principal.getUsername() : "anonymous";
        return ApiResponse.ok("Incident reported", incidentService.report(request, username));
    }

    @GetMapping
    public ApiResponse list(@RequestParam(required = false) IncidentStatus status) {
        IncidentListResponse response = incidentService.list(status);
        return ApiResponse.ok("Incidents", response);
    }

    @GetMapping("/{id}")
    public ApiResponse get(@PathVariable Long id) {
        return ApiResponse.ok("Incident", incidentService.get(id));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','TRAFFIC_OPERATOR','EMERGENCY_OPERATOR')")
    public ApiResponse resolve(@PathVariable Long id) {
        return ApiResponse.ok("Incident resolved", incidentService.resolve(id));
    }
}
