package com.resqroute.config;

import com.resqroute.entity.EmergencyUnit;
import com.resqroute.entity.Hospital;
import com.resqroute.entity.Intersection;
import com.resqroute.entity.Road;
import com.resqroute.entity.User;
import com.resqroute.enums.Role;
import com.resqroute.enums.TrafficLevel;
import com.resqroute.enums.UnitType;
import com.resqroute.repository.EmergencyUnitRepository;
import com.resqroute.repository.HospitalRepository;
import com.resqroute.repository.IntersectionRepository;
import com.resqroute.repository.RoadRepository;
import com.resqroute.repository.UserRepository;
import com.resqroute.graph.GraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo city network: 12 intersections, 18 roads, 3 hospitals,
 * 5 emergency units and one demo account per role.
 * Runs only when the users table is empty.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final IntersectionRepository intersections;
    private final RoadRepository roads;
    private final HospitalRepository hospitals;
    private final EmergencyUnitRepository units;
    private final PasswordEncoder passwordEncoder;
    private final GraphBuilder graphBuilder;

    @Value("${resqroute.seed.enabled}")
    private boolean seedEnabled;

    public DataSeeder(UserRepository users, IntersectionRepository intersections,
                      RoadRepository roads, HospitalRepository hospitals,
                      EmergencyUnitRepository units, PasswordEncoder passwordEncoder,
                      GraphBuilder graphBuilder) {
        this.users = users;
        this.intersections = intersections;
        this.roads = roads;
        this.hospitals = hospitals;
        this.units = units;
        this.passwordEncoder = passwordEncoder;
        this.graphBuilder = graphBuilder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled || users.count() > 0) {
            return;
        }
        log.info("Seeding demo data...");

        seedUsers();
        seedNetwork();
        seedHospitals();
        seedUnits();

        graphBuilder.rebuild();
        log.info("Seed complete: {} users, {} intersections, {} roads, {} hospitals, {} units",
                users.count(), intersections.count(), roads.count(), hospitals.count(), units.count());
    }

    private void seedUsers() {
        users.save(new User("admin", "admin@resqroute.io", passwordEncoder.encode("admin123"), Role.ADMIN));
        users.save(new User("traffic", "traffic@resqroute.io", passwordEncoder.encode("traffic123"), Role.TRAFFIC_OPERATOR));
        users.save(new User("responder", "responder@resqroute.io", passwordEncoder.encode("responder123"), Role.EMERGENCY_OPERATOR));
        users.save(new User("citizen", "citizen@resqroute.io", passwordEncoder.encode("citizen123"), Role.USER));
    }

    private void seedNetwork() {
        Intersection hub      = intersections.save(new Intersection("Central Hub", 300, 200));
        Intersection north    = intersections.save(new Intersection("North Gate", 300, 60));
        Intersection northE   = intersections.save(new Intersection("North East", 470, 90));
        Intersection east     = intersections.save(new Intersection("East Market", 540, 220));
        Intersection southE   = intersections.save(new Intersection("South East", 470, 350));
        Intersection south    = intersections.save(new Intersection("South Gate", 300, 380));
        Intersection southW   = intersections.save(new Intersection("South West", 130, 350));
        Intersection west     = intersections.save(new Intersection("West Park", 60, 220));
        Intersection northW   = intersections.save(new Intersection("North West", 130, 90));
        Intersection hospital = intersections.save(new Intersection("Hospital Row", 470, 160));
        Intersection industry = intersections.save(new Intersection("Industrial Belt", 130, 250));
        Intersection airport  = intersections.save(new Intersection("Airport Link", 60, 60));

        road(hub, north, 3.1, 50);
        road(hub, east, 3.4, 45);
        road(hub, south, 3.0, 50);
        road(hub, west, 3.2, 45);
        road(hub, hospital, 2.4, 40);
        road(north, northE, 3.0, 50);
        road(north, northW, 3.0, 50);
        road(northE, east, 3.1, 45);
        road(northE, hospital, 2.6, 40);
        road(east, southE, 3.0, 45);
        road(southE, south, 3.0, 50);
        road(south, southW, 3.0, 50);
        road(southW, west, 3.1, 45);
        road(west, northW, 3.0, 45);
        road(west, industry, 1.8, 35);
        road(industry, hub, 3.3, 40);
        road(airport, northW, 2.2, 60);
        road(airport, north, 4.5, 70);

        roads.findAll().stream().filter(r -> r.getName().startsWith("Central Hub"))
                .findFirst()
                .ifPresent(r -> r.setTrafficLevel(TrafficLevel.MODERATE));
        roads.saveAll(roads.findAll());
    }

    private void road(Intersection from, Intersection to, double km, double speed) {
        roads.save(new Road(from.getName() + " - " + to.getName(), from, to, km, speed));
    }

    private void seedHospitals() {
        Intersection hospitalNode = intersections.findAll().stream()
                .filter(n -> n.getName().equals("Hospital Row")).findFirst().orElseThrow();
        Intersection hub = intersections.findAll().stream()
                .filter(n -> n.getName().equals("Central Hub")).findFirst().orElseThrow();
        Intersection south = intersections.findAll().stream()
                .filter(n -> n.getName().equals("South Gate")).findFirst().orElseThrow();

        hospitals.save(new Hospital("City General Hospital", hospitalNode, 120, 64, true));
        hospitals.save(new Hospital("Central Care Clinic", hub, 40, 18, false));
        hospitals.save(new Hospital("Southside Trauma Centre", south, 80, 42, true));
    }

    private void seedUnits() {
        var stationNodes = intersections.findAll();
        Intersection hub = stationNodes.stream().filter(n -> n.getName().equals("Central Hub")).findFirst().orElseThrow();
        Intersection north = stationNodes.stream().filter(n -> n.getName().equals("North Gate")).findFirst().orElseThrow();
        Intersection south = stationNodes.stream().filter(n -> n.getName().equals("South Gate")).findFirst().orElseThrow();
        Intersection west = stationNodes.stream().filter(n -> n.getName().equals("West Park")).findFirst().orElseThrow();
        Intersection hospital = stationNodes.stream().filter(n -> n.getName().equals("Hospital Row")).findFirst().orElseThrow();

        units.save(new EmergencyUnit("Medic One", UnitType.AMBULANCE, hospital, "101"));
        units.save(new EmergencyUnit("Medic Two", UnitType.AMBULANCE, hub, "102"));
        units.save(new EmergencyUnit("Engine 7", UnitType.FIRE_TRUCK, west, "103"));
        units.save(new EmergencyUnit("Patrol 3", UnitType.POLICE, north, "104"));
        units.save(new EmergencyUnit("Patrol 5", UnitType.POLICE, south, "105"));
    }
}
