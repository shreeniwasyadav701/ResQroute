package com.resqroute.repository;

import com.resqroute.entity.EmergencyUnit;
import com.resqroute.enums.UnitStatus;
import com.resqroute.enums.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyUnitRepository extends JpaRepository<EmergencyUnit, Long> {
    List<EmergencyUnit> findByStatus(UnitStatus status);
    List<EmergencyUnit> findByStatusAndType(UnitStatus status, UnitType type);
    long countByStatus(UnitStatus status);
}
