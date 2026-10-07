package com.resqroute.repository;

import com.resqroute.entity.Incident;
import com.resqroute.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatusOrderByCreatedAtDesc(IncidentStatus status);
    List<Incident> findAllByOrderByCreatedAtDesc();
    List<Incident> findTop5ByOrderByCreatedAtDesc();
    long countByStatus(IncidentStatus status);
}
