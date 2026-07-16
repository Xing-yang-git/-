package com.platform.repository;

import com.platform.model.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByBuildingId(Long buildingId);
    Optional<Unit> findByBuildingIdAndName(Long buildingId, String name);
}
