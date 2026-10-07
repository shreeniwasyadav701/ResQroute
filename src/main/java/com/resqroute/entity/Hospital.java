package com.resqroute.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "hospitals")
public class Hospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Intersection location;

    @Column(nullable = false)
    private int bedsTotal;

    @Column(nullable = false)
    private int bedsAvailable;

    @Column(nullable = false)
    private boolean traumaCenter;

    @Column(nullable = false)
    private boolean emergencyReady = true;

    public Hospital() {
    }

    public Hospital(String name, Intersection location, int bedsTotal, int bedsAvailable, boolean traumaCenter) {
        this.name = name;
        this.location = location;
        this.bedsTotal = bedsTotal;
        this.bedsAvailable = bedsAvailable;
        this.traumaCenter = traumaCenter;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Intersection getLocation() { return location; }
    public void setLocation(Intersection location) { this.location = location; }
    public int getBedsTotal() { return bedsTotal; }
    public void setBedsTotal(int bedsTotal) { this.bedsTotal = bedsTotal; }
    public int getBedsAvailable() { return bedsAvailable; }
    public void setBedsAvailable(int bedsAvailable) { this.bedsAvailable = bedsAvailable; }
    public boolean isTraumaCenter() { return traumaCenter; }
    public void setTraumaCenter(boolean traumaCenter) { this.traumaCenter = traumaCenter; }
    public boolean isEmergencyReady() { return emergencyReady; }
    public void setEmergencyReady(boolean emergencyReady) { this.emergencyReady = emergencyReady; }

    public boolean canAccept() {
        return emergencyReady && bedsAvailable > 0;
    }
}
