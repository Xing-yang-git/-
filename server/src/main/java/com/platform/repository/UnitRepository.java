package com.platform.repository;

import com.platform.model.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {
    List<Unit> findByBuildingId(UUID buildingId);
    Optional<Unit> findByBuildingIdAndName(UUID buildingId, String name);
}
