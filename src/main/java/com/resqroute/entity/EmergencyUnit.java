package com.resqroute.entity;

import com.resqroute.enums.UnitStatus;
import com.resqroute.enums.UnitType;
import jakarta.persistence.*;

@Entity
@Table(name = "emergency_units")
public class EmergencyUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitStatus status = UnitStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_node_id", nullable = false)
    private Intersection station;

    /** Live position, defaults to the station until the unit is moved. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_node_id", nullable = false)
    private Intersection currentLocation;

    @Column(length = 30)
    private String phone;

    public EmergencyUnit() {
    }

    public EmergencyUnit(String name, UnitType type, Intersection station, String phone) {
        this.name = name;
        this.type = type;
        this.station = station;
        this.currentLocation = station;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UnitType getType() { return type; }
    public void setType(UnitType type) { this.type = type; }
    public UnitStatus getStatus() { return status; }
    public void setStatus(UnitStatus status) { this.status = status; }
    public Intersection getStation() { return station; }
    public void setStation(Intersection station) { this.station = station; }
    public Intersection getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Intersection currentLocation) { this.currentLocation = currentLocation; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
