package com.resqroute.dto;

import com.resqroute.enums.IncidentStatus;
import com.resqroute.enums.IncidentType;
import com.resqroute.enums.Severity;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record IncidentCreateRequest(
            @NotNull IncidentType type,
            @NotNull Severity severity,
            String description,
            @NotNull Long locationId,
            Long affectedRoadId) {
    }

    public record IncidentResponse(
            Long id,
            IncidentType type,
            Severity severity,
            IncidentStatus status,
            String description,
            Long locationId,
            String locationName,
            Long affectedRoadId,
            String affectedRoadName,
            String reportedBy,
            Long assignedUnitId,
            String assignedUnitName,
            Instant createdAt,
            Instant resolvedAt) {
    }

    public record IncidentListResponse(List<IncidentResponse> incidents) {
    }

    public record TrafficUpdateResponse(
            Long roadId,
            String roadName,
            String trafficLevel,
            String status,
            long activeIncidents) {
    }
}
