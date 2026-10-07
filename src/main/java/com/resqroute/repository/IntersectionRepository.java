package com.resqroute.repository;

import com.resqroute.entity.Intersection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntersectionRepository extends JpaRepository<Intersection, Long> {
    List<Intersection> findAllByOrderByXAscYAsc();
}
