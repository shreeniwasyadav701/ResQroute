package com.resqroute.entity;

import jakarta.persistence.*;

/**
 * An intersection of the road network. Graph nodes are these intersections,
 * the roads below are the weighted edges between them.
 */
@Entity
@Table(name = "intersections")
public class Intersection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    public Intersection() {
    }

    public Intersection(String name, double x, double y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
}
