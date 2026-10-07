package com.resqroute.dto;

import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.TrafficLevel;
import com.resqroute.enums.UnitStatus;
import com.resqroute.enums.UnitType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ResourceDtos {

    private ResourceDtos() {
    }

    public record ApiResponse(boolean success, String message, Object data) {
        public static ApiResponse ok(String message) {
            return new ApiResponse(true, message, null);
        }

        public static ApiResponse ok(String message, Object data) {
            return new ApiResponse(true, message, data);
        }

        public static ApiResponse fail(String message) {
            return new ApiResponse(false, message, null);
        }
    }

    public record IntersectionRequest(@NotBlank String name,
                                      @NotNull Double x,
                                      @NotNull Double y) {
    }

    public record IntersectionResponse(Long id, String name, double x, double y) {
    }

    public record RoadRequest(@NotBlank String name,
                              @NotNull Long fromNodeId,
                              @NotNull Long toNodeId,
                              @NotNull @Min(1) Double distanceKm,
                              @NotNull @Min(1) Double baseSpeedKmh,
                              boolean oneway) {
    }

    public record RoadUpdateTrafficRequest(TrafficLevel trafficLevel, RoadStatus status) {
    }

    public record RoadResponse(Long id, String name,
                               Long fromNodeId, String fromNodeName,
                               Long toNodeId, String toNodeName,
                               double distanceKm, double baseSpeedKmh,
                               TrafficLevel trafficLevel, RoadStatus status,
                               boolean oneway, double travelTimeMinutes) {
    }

    public record RoadListResponse(List<RoadResponse> roads) {
    }

    public record UnitRequest(@NotBlank String name,
                              @NotNull UnitType type,
                              @NotNull Long stationNodeId,
                              String phone) {
    }

    public record UnitResponse(Long id, String name, UnitType type, UnitStatus status,
                               Long stationNodeId, String stationName,
                               Long currentNodeId, String currentLocationName,
                               String phone) {
    }

    public record UnitListResponse(List<UnitResponse> units) {
    }

    public record HospitalRequest(@NotBlank String name,
                                  @NotNull Long locationId,
                                  @NotNull Integer bedsTotal,
                                  @NotNull Integer bedsAvailable,
                                  boolean traumaCenter) {
    }

    public record HospitalResponse(Long id, String name,
                                   Long locationId, String locationName,
                                   int bedsTotal, int bedsAvailable,
                                   boolean traumaCenter, boolean emergencyReady) {
    }

    public record HospitalListResponse(List<HospitalResponse> hospitals) {
    }

    public record HospitalRecommendation(
            HospitalResponse hospital,
            double etaMinutes,
            double distanceKm,
            int rank,
            String reason) {
    }

    public record HospitalRecommendationResponse(
            Long incidentId,
            Long fromNodeId,
            String fromNodeName,
            List<HospitalRecommendation> recommendations) {
    }

    public record DispatchRequest(Long incidentId, Long unitId) {
    }

    public record DispatchResponse(
            Long incidentId,
            Long unitId,
            String unitName,
            UnitType unitType,
            double etaMinutes,
            double distanceKm,
            List<String> routeNodes,
            List<String> routeRoads,
            String message) {
    }

    public record UnitEtaResponse(
            Long unitId,
            String unitName,
            UnitType unitType,
            UnitStatus status,
            boolean eligible,
            double etaMinutes,
            double distanceKm) {
    }

    public record DashboardResponse(
            long activeIncidents,
            long resolvedIncidents,
            long availableUnits,
            long busyUnits,
            long blockedRoads,
            long openRoads,
            long totalBedsAvailable,
            long users,
            List<IncidentDtos.IncidentResponse> recentIncidents) {
    }
}
