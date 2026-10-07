# ResQRoute — Intelligent Traffic & Emergency Response System

Full-stack project: report an incident, watch the road network degrade, get the fastest /
shortest / low-traffic route recalculated, dispatch the nearest emergency unit and rank
hospitals by ambulance ETA. Built with Spring Boot, MySQL, Dijkstra and a live SVG dashboard.

---

## 1. Architecture

```
Browser (HTML/CSS/JS + SVG map + WebSocket)
   │  REST (JSON, JWT)                        │  /ws live frames
   ▼                                          ▼
Spring Boot  ── Controller ── Service ── Graph (Dijkstra) ── MySQL / H2
                  │              │
              Spring Security  JPA Repositories
              (JWT + roles)
```

Layers: `controller → service → repository → entity`, with the routing objective isolated
in `graph/` (adjacency list + Dijkstra) so the algorithm is testable without Spring.

## 2. Tech stack

| Layer      | Choice |
|------------|--------|
| Backend    | Java 17, Spring Boot 3.5 (Web, Data JPA, Security, Validation, WebSocket) |
| Auth       | JWT (jjwt 0.12), BCrypt, role-based `@PreAuthorize` |
| Database   | MySQL 8 (default), H2 in MySQL mode (`h2` profile / tests) |
| DSA        | Adjacency list + `PriorityQueue` Dijkstra, three weight functions |
| Frontend   | Vanilla HTML/CSS/JS, SVG network map, WebSocket live refresh |
| Build      | Maven |

## 3. Run it

### Prerequisites
- JDK 17+ (tested on 21)
- Maven 3.9+
- MySQL 8 (optional — use the `h2` profile if you have none)

### Database (MySQL)
```sql
CREATE DATABASE resqroute;
CREATE USER 'resqroute'@'localhost' IDENTIFIED BY 'resqroute';
GRANT ALL ON resqroute.* TO 'resqroute'@'localhost';
```
Override with env vars `DB_USER` / `DB_PASSWORD` if different.

### Start
```bash
mvn spring-boot:run                  # MySQL (default profile)
mvn spring-boot:run -Dspring-boot.run.profiles=h2   # in-memory H2, no setup
```
App: http://localhost:8080

Seeded on first start (12 intersections, 18 roads, 3 hospitals, 5 units, 4 accounts):

| Username   | Password     | Role                |
|------------|--------------|---------------------|
| admin      | admin123     | ADMIN               |
| traffic    | traffic123   | TRAFFIC_OPERATOR    |
| responder  | responder123 | EMERGENCY_OPERATOR  |
| citizen    | citizen123   | USER                |

### Tests
```bash
mvn test            # Dijkstra unit tests + full API/security integration tests (H2)
```

## 4. Domain model

| Table | Purpose |
|-------|---------|
| `users` | Accounts and roles: USER, ADMIN, TRAFFIC_OPERATOR, EMERGENCY_OPERATOR |
| `intersections` | Graph nodes (name + x/y for the SVG map) |
| `roads` | Graph edges: distance, base speed, `traffic_level`, `status`, one-way flag |
| `incidents` | Type, severity, status, location, affected road, assigned unit |
| `emergency_units` | Police / ambulance / fire truck, status, station and live position |
| `hospitals` | Capacity, trauma flag, location |
| `route_requests` | Persisted route searches for history and re-computation |

## 5. DSA design

**Representation.** Intersections are nodes, roads are weighted edges, stored as an
adjacency list (`Map<Long, List<Edge>>`, see `graph/RoadGraph.java`). Two-way roads are
inserted in both directions; one-way roads only once. Space `O(V + E)`.

**Algorithm.** `graph/Dijkstra.java` — classic Dijkstra with a `PriorityQueue<State>`
settle-once loop, reconstructing the path from a `cameFrom` edge map.
Time `O((V + E) log V)`, space `O(V + E)`.

**Three objectives, one algorithm.** The weight function is injected:

| Preference | Edge weight | Minimises |
|------------|-------------|-----------|
| `FASTEST` | `distance / (baseSpeed × trafficSpeedFactor)` | travel time |
| `SHORTEST` | `distance` | kilometres |
| `LOW_TRAFFIC` | `distance × (1 + congestionPenalty)` | congestion exposure |

Traffic factors: FREE_FLOW 1.0, MODERATE 0.8, HEAVY 0.5, GRIDLOCK 0.25.
Roads in `BLOCKED`/`CLOSED` state are skipped entirely, so incidents automatically
reroute every calculation.

**Emergency ETA** runs the same single-pair shortest path from each unit's position to the
incident and picks the minimum among available, type-compatible units.
**Hospital ranking** ranks by ambulance ETA, drops facilities without free beds, and breaks
ties on available beds.

## 6. Incident → traffic → routing pipeline

```
POST /api/incidents (severity CRITICAL)
   → affected road set to BLOCKED / GRIDLOCK
   → graph rebuilt in memory
   → stored routes re-computed and flagged
   → WebSocket frame: INCIDENT + TRAFFIC + ROUTE_REFRESH
   → dashboards refresh, map edges recolour
POST /api/incidents/{id}/resolve
   → road restored to OPEN / FREE_FLOW, unit released, routes recomputed
```

Severity mapping: `CRITICAL → BLOCKED + GRIDLOCK`, `HIGH → GRIDLOCK`, `MEDIUM → HEAVY`,
`LOW → MODERATE`.

## 7. API reference

Base: `/api` — all endpoints except `register`, `login` and public network reads require
`Authorization: Bearer <jwt>`.

**Auth**
```
POST /api/auth/register      {username, email, password}          public (role USER)
POST /api/auth/login         {username, password}                 public → {token, role}
GET  /api/auth/me                                              authenticated
```

**Network**
```
GET  /api/network/intersections                                   public
POST /api/network/intersections   {name, x, y}                    ADMIN, TRAFFIC_OPERATOR
GET  /api/network/roads                                           public
POST /api/network/roads           {name, fromNodeId, toNodeId, distanceKm, baseSpeedKmh, oneway}
PATCH /api/network/roads/{id}/traffic?trafficLevel=&status=       ADMIN, TRAFFIC_OPERATOR
```

**Routing**
```
POST /api/routes/compute         {sourceId, destinationId, preference?}
                                  → 3 options: FASTEST, SHORTEST, LOW_TRAFFIC
GET  /api/routes/history?limit=20
POST /api/routes/recompute                                   ADMIN, TRAFFIC, EMERGENCY
```

**Incidents**
```
POST /api/incidents              {type, severity, description?, locationId, affectedRoadId?}
GET  /api/incidents?status=ACTIVE|RESOLVED
GET  /api/incidents/{id}
POST /api/incidents/{id}/resolve                             ADMIN, TRAFFIC, EMERGENCY
```

**Emergency**
```
GET  /api/emergency/units
POST /api/emergency/units        {name, type, stationNodeId, phone}   ADMIN, EMERGENCY
PATCH /api/emergency/units/{id}/status?status=AVAILABLE|BUSY|OFFLINE
GET  /api/emergency/etas?incidentId=&type=          ETA of every unit, sorted
POST /api/emergency/dispatch     {incidentId, unitId?}   unitId omitted = auto-pick fastest
```

**Hospitals**
```
GET  /api/hospitals
POST /api/hospitals              {name, locationId, bedsTotal, bedsAvailable, traumaCenter}  ADMIN
PATCH /api/hospitals/{id}/beds?available=12                     ADMIN, EMERGENCY
GET  /api/hospitals/recommend?incidentId= | fromNodeId=&minBeds=&limit=
```

**Dashboard / admin**
```
GET  /api/dashboard/summary      KPI counters + recent incidents
GET  /api/admin/users                                            ADMIN
POST /api/admin/users/{id}/role   {role}                          ADMIN
POST /api/admin/users/{id}/enabled?enabled=false                  ADMIN
POST /api/admin/operators         {username, email, password, role}  ADMIN
```

WebSocket: `ws://localhost:8080/ws`, frames `{type: INCIDENT|TRAFFIC|UNIT|ROUTE_REFRESH, payload, timestamp}`.

## 8. Frontend

Single page (`static/index.html`) with role-aware navigation:

- **Map & Routes** — SVG network, traffic-coloured edges, click-to-pick source/destination,
  three route cards with animated path highlight.
- **Incidents** — report with severity/location/road, list with live status, resolve button.
- **Emergency** — ETA table per unit, auto-dispatch or per-unit dispatch, unit roster.
- **Hospitals** — capacity board + ETA-ranked recommendations.
- **Traffic Control** — per-road traffic level and status editing (operators).
- **Admin** — user roles, operator provisioning, unit creation.

## 9. Project layout

```
ResQRoute/
├── pom.xml
├── src/main/java/com/resqroute/
│   ├── ResQRouteApplication.java
│   ├── config/          SecurityConfig, WebSocketConfig, DataSeeder
│   ├── security/        JwtService, JwtAuthFilter, AppUserDetailsService
│   ├── entity/          User, Intersection, Road, Incident, EmergencyUnit, Hospital, RouteRequest
│   ├── repository/      Spring Data JPA repositories
│   ├── enums/           Role, Severity, TrafficLevel, RoutePreference, ...
│   ├── graph/           RoadGraph (adjacency list), Dijkstra, GraphBuilder (weights)
│   ├── service/         Routing, Incident, Emergency, Hospital, Network, Auth, Admin, Dashboard
│   ├── controller/      REST endpoints
│   ├── dto/             Request/response records with validation
│   ├── websocket/       LiveUpdateHandler, EventBroadcaster
│   └── exception/       ApiException, GlobalExceptionHandler
├── src/main/resources/
│   ├── application.yml / application-h2.yml
│   └── static/          index.html, css/style.css, js/{api,map,app}.js
└── src/test/java/com/resqroute/
    ├── DijkstraTest.java          algorithm correctness
    └── ApiIntegrationTest.java    auth, roles, routing, incident, dispatch, hospitals
```

## 10. Roadmap coverage

| Phase | Status |
|-------|--------|
| 1–6 Planning, env, Java, DB, REST, auth/roles | done |
| 7 Road graph & Dijkstra | done (`graph/`) |
| 8 Incident management | done |
| 9 Dynamic traffic & routing (3 route options) | done |
| 10 Emergency response (ETA + dispatch) | done |
| 11 Hospital module (ETA ranking) | done |
| 12 Frontend dashboards | done |
| 13 Real-time (WebSocket) | done |
| 14 Testing (unit + integration) | done |
| 15 Advanced (maps, A*, analytics, Docker) | future work |
| 16 GitHub + README | README here; repo hygiene on you |
| 17 Resume/interview prep | talk through the DSA and trade-offs in this file |
