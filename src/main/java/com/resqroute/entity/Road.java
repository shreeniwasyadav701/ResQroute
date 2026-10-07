package com.resqroute.entity;

import com.resqroute.enums.RoadStatus;
import com.resqroute.enums.TrafficLevel;
import jakarta.persistence.*;

@Entity
@Table(name = "roads")
public class Road {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_node", nullable = false)
    private Intersection fromNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_node", nullable = false)
    private Intersection toNode;

    @Column(nullable = false)
    private double distanceKm;

    /** Posted speed limit of the segment in km/h before congestion is applied. */
    @Column(nullable = false)
    private double baseSpeedKmh = 40;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrafficLevel trafficLevel = TrafficLevel.FREE_FLOW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoadStatus status = RoadStatus.OPEN;

    @Column(nullable = false)
    private boolean oneway = false;

    public Road() {
    }

    public Road(String name, Intersection fromNode, Intersection toNode, double distanceKm, double baseSpeedKmh) {
        this.name = name;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.distanceKm = distanceKm;
        this.baseSpeedKmh = baseSpeedKmh;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Intersection getFromNode() { return fromNode; }
    public void setFromNode(Intersection fromNode) { this.fromNode = fromNode; }
    public Intersection getToNode() { return toNode; }
    public void setToNode(Intersection toNode) { this.toNode = toNode; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public double getBaseSpeedKmh() { return baseSpeedKmh; }
    public void setBaseSpeedKmh(double baseSpeedKmh) { this.baseSpeedKmh = baseSpeedKmh; }
    public TrafficLevel getTrafficLevel() { return trafficLevel; }
    public void setTrafficLevel(TrafficLevel trafficLevel) { this.trafficLevel = trafficLevel; }
    public RoadStatus getStatus() { return status; }
    public void setStatus(RoadStatus status) { this.status = status; }
    public boolean isOneway() { return oneway; }
    public void setOneway(boolean oneway) { this.oneway = oneway; }

    public boolean isTraversable() {
        return status == RoadStatus.OPEN;
    }

    public double effectiveSpeedKmh() {
        return Math.max(5.0, baseSpeedKmh * trafficLevel.getSpeedFactor());
    }

    public double travelTimeMinutes() {
        return (distanceKm / effectiveSpeedKmh()) * 60.0;
    }
}
