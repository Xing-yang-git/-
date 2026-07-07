package com.platform.repository;

import com.platform.model.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuildingRepository extends JpaRepository<Building, UUID> {
    List<Building> findByTenantId(UUID tenantId);
    Optional<Building> findByTenantIdAndName(UUID tenantId, String name);
}
