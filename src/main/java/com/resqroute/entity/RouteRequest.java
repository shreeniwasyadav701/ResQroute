package com.resqroute.entity;

import com.resqroute.enums.RoutePreference;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persisted route search. Stores the request plus the summary of the winning
 * route so history and re-computation after an incident are possible.
 */
@Entity
@Table(name = "route_requests")
public class RouteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Intersection source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Intersection destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoutePreference preference = RoutePreference.FASTEST;

    @Column(length = 60)
    private String requestedBy;

    @Column(length = 400)
    private String pathSummary;

    private Double distanceKm;

    private Double timeMinutes;

    /** Stored so the dashboard can flag routes invalidated by a new incident. */
    @Column(length = 400)
    private String affectedRoadIds;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RouteRequest() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Intersection getSource() { return source; }
    public void setSource(Intersection source) { this.source = source; }
    public Intersection getDestination() { return destination; }
    public void setDestination(Intersection destination) { this.destination = destination; }
    public RoutePreference getPreference() { return preference; }
    public void setPreference(RoutePreference preference) { this.preference = preference; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getPathSummary() { return pathSummary; }
    public void setPathSummary(String pathSummary) { this.pathSummary = pathSummary; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Double getTimeMinutes() { return timeMinutes; }
    public void setTimeMinutes(Double timeMinutes) { this.timeMinutes = timeMinutes; }
    public String getAffectedRoadIds() { return affectedRoadIds; }
    public void setAffectedRoadIds(String affectedRoadIds) { this.affectedRoadIds = affectedRoadIds; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
