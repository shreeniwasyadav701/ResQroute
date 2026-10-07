package com.resqroute.repository;

import com.resqroute.entity.Intersection;
import com.resqroute.entity.Road;
import com.resqroute.enums.RoadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadRepository extends JpaRepository<Road, Long> {
    List<Road> findByStatus(RoadStatus status);
    List<Road> findByFromNodeOrToNode(Intersection from, Intersection to);
}
