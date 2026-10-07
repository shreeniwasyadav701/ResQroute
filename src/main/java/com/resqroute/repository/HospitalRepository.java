package com.resqroute.repository;

import com.resqroute.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findAllByOrderByBedsAvailableDesc();
}
