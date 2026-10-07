package com.resqroute.controller;

import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.dto.RouteDtos.RouteHistoryResponse;
import com.resqroute.dto.RouteDtos.RoutePlanResponse;
import com.resqroute.dto.RouteDtos.RouteQuery;
import com.resqroute.service.RoutingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    /** Returns Fastest, Shortest and Low-Traffic options in one call. */
    @PostMapping("/compute")
    public ApiResponse compute(@Valid @RequestBody RouteQuery query,
                               @AuthenticationPrincipal UserDetails principal) {
        String username = principal != null ? principal.getUsername() : "anonymous";
        RoutePlanResponse plan = routingService.computeRoutes(query, username);
        return ApiResponse.ok("Routes computed", plan);
    }

    @GetMapping("/history")
    public ApiResponse history(@RequestParam(defaultValue = "20") int limit) {
        RouteHistoryResponse history = routingService.history(Math.min(100, Math.max(1, limit)));
        return ApiResponse.ok("Route history", history);
    }

    @PostMapping("/recompute")
    @PreAuthorize("hasAnyRole('ADMIN','TRAFFIC_OPERATOR','EMERGENCY_OPERATOR')")
    public ApiResponse recompute() {
        int touched = routingService.recomputeStoredRoutes();
        return ApiResponse.ok("Recomputed " + touched + " stored routes", touched);
    }
}
