package com.resqroute.dto;

import com.resqroute.enums.RoutePreference;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class RouteDtos {

    private RouteDtos() {
    }

    public record RouteQuery(
            @NotNull Long sourceId,
            @NotNull Long destinationId,
            RoutePreference preference) {
    }

    /**
     * One computed route option. `score` is the raw Dijkstra cost for the
     * preference that produced it; `timeMinutes` and `distanceKm` are the real
     * world measurements of the path.
     */
    public record RouteResultDto(
            RoutePreference preference,
            boolean found,
            List<Long> nodeIds,
            List<String> nodeNames,
            List<Long> roadIds,
            List<String> roadNames,
            double distanceKm,
            double timeMinutes,
            double score,
            double avgSpeedKmh,
            String summary) {

        public static RouteResultDto notFound(RoutePreference preference) {
            return new RouteResultDto(preference, false, List.of(), List.of(), List.of(),
                    List.of(), 0, 0, Double.POSITIVE_INFINITY, 0, "No traversable path");
        }
    }

    public record RoutePlanResponse(
            Long requestId,
            String sourceName,
            String destinationName,
            List<RouteResultDto> routes) {
    }

    public record RouteRequestResponse(
            Long id,
            Long sourceId,
            String sourceName,
            Long destinationId,
            String destinationName,
            RoutePreference preference,
            String pathSummary,
            Double distanceKm,
            Double timeMinutes,
            Instant createdAt,
            String requestedBy) {
    }

    public record RouteHistoryResponse(List<RouteRequestResponse> requests) {
    }
}
