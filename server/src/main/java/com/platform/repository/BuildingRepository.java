package com.platform.repository;

import com.platform.model.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findByTenantId(Long tenantId);
    Optional<Building> findByTenantIdAndName(Long tenantId, String name);
}
