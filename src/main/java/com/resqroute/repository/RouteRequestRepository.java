package com.resqroute.repository;

import com.resqroute.entity.RouteRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRequestRepository extends JpaRepository<RouteRequest, Long> {
    List<RouteRequest> findAllByOrderByCreatedAtDesc();
    List<RouteRequest> findTop20ByOrderByCreatedAtDesc();
}
