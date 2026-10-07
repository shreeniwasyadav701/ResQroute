package com.resqroute.controller;

import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.dto.ResourceDtos.IntersectionRequest;
import com.resqroute.dto.ResourceDtos.RoadListResponse;
import com.resqroute.dto.ResourceDtos.RoadRequest;
import com.resqroute.dto.ResourceDtos.RoadResponse;
import com.resqroute.service.NetworkService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkService networkService;

    public NetworkController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping("/intersections")
    public ApiResponse intersections() {
        return ApiResponse.ok("Intersections", networkService.listIntersections());
    }

    @PostMapping("/intersections")
    @PreAuthorize("hasAnyRole('ADMIN','TRAFFIC_OPERATOR')")
    public ApiResponse createIntersection(@Valid @RequestBody IntersectionRequest request) {
        return ApiResponse.ok("Intersection created", networkService.createIntersection(request));
    }

    @GetMapping("/roads")
    public ApiResponse roads() {
        return ApiResponse.ok("Roads", networkService.listRoads());
    }

    @PostMapping("/roads")
    @PreAuthorize("hasAnyRole('ADMIN','TRAFFIC_OPERATOR')")
    public ApiResponse createRoad(@Valid @RequestBody RoadRequest request) {
        return ApiResponse.ok("Road created", networkService.createRoad(request));
    }

    @PatchMapping("/roads/{id}/traffic")
    @PreAuthorize("hasAnyRole('ADMIN','TRAFFIC_OPERATOR')")
    public ApiResponse updateTraffic(@PathVariable Long id,
                                     @RequestParam(required = false) com.resqroute.enums.TrafficLevel trafficLevel,
                                     @RequestParam(required = false) com.resqroute.enums.RoadStatus status) {
        RoadResponse road = networkService.updateTraffic(id, trafficLevel, status);
        return ApiResponse.ok("Road traffic updated", road);
    }
}
