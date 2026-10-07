package com.resqroute.controller;

import com.resqroute.dto.ResourceDtos.ApiResponse;
import com.resqroute.service.DashboardService;
import com.resqroute.service.NetworkService;
import com.resqroute.websocket.LiveUpdateHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final NetworkService networkService;
    private final LiveUpdateHandler liveUpdateHandler;

    public DashboardController(DashboardService dashboardService,
                               NetworkService networkService,
                               LiveUpdateHandler liveUpdateHandler) {
        this.dashboardService = dashboardService;
        this.networkService = networkService;
        this.liveUpdateHandler = liveUpdateHandler;
    }

    @GetMapping("/summary")
    public ApiResponse summary() {
        return ApiResponse.ok("Dashboard summary", dashboardService.summary());
    }

    @GetMapping("/graph")
    public ApiResponse graph() {
        networkService.refreshGraph();
        return ApiResponse.ok("Graph rebuilt");
    }

    @GetMapping("/live")
    public ApiResponse live() {
        return ApiResponse.ok("Live connections", liveUpdateHandler.connectedClients());
    }
}
