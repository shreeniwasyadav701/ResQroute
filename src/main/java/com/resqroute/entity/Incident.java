package com.resqroute.entity;

import com.resqroute.enums.IncidentStatus;
import com.resqroute.enums.IncidentType;
import com.resqroute.enums.Severity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.ACTIVE;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Intersection location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_road_id")
    private Road affectedRoad;

    @Column(length = 60)
    private String reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_unit_id")
    private EmergencyUnit assignedUnit;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    public Incident() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public IncidentType getType() { return type; }
    public void setType(IncidentType type) { this.type = type; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Intersection getLocation() { return location; }
    public void setLocation(Intersection location) { this.location = location; }
    public Road getAffectedRoad() { return affectedRoad; }
    public void setAffectedRoad(Road affectedRoad) { this.affectedRoad = affectedRoad; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public EmergencyUnit getAssignedUnit() { return assignedUnit; }
    public void setAssignedUnit(EmergencyUnit assignedUnit) { this.assignedUnit = assignedUnit; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
