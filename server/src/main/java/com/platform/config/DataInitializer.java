package com.platform.config;

import com.platform.model.entity.Building;
import com.platform.model.entity.Room;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.Unit;
import com.platform.model.entity.User;
import com.platform.repository.BuildingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import com.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds the initial admin account and tenant data on first startup.
 * Safe to re-run — skips if data already exists.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           TenantRepository tenantRepository,
                           BuildingRepository buildingRepository,
                           UnitRepository unitRepository,
                           RoomRepository roomRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.roomRepository = roomRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedTenant();
    }

    private void seedAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            log.info("Admin user already exists, skipping seed");
            return;
        }

        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .name("系统管理员")
                .userType("admin")
                .authStatus("approved")
                .build();
        userRepository.save(admin);
        log.info("Created admin user: admin / admin123");
    }

    private void seedTenant() {
        if (tenantRepository.count() > 0) {
            log.info("Tenants already exist, skipping seed");
            return;
        }

        Tenant tenant = Tenant.builder()
                .name("翠湖花园")
                .build();
        tenant = tenantRepository.save(tenant);
        log.info("Created tenant: 翠湖花园");

        UUID tenantId = tenant.getId();

        // Create 8 buildings, each with 3 units, each with 4 rooms (1-8栋 × 1-3单元 × 101-804)
        for (int b = 1; b <= 8; b++) {
            Building building = Building.builder()
                    .tenantId(tenantId)
                    .name(b + "栋")
                    .build();
            building = buildingRepository.save(building);
            UUID buildingId = building.getId();

            for (int u = 1; u <= 3; u++) {
                Unit unit = Unit.builder()
                        .buildingId(buildingId)
                        .name(u + "单元")
                        .build();
                unit = unitRepository.save(unit);
                UUID unitId = unit.getId();

                for (int r = 1; r <= 4; r++) {
                    String roomNumber = (b * 100 + r) + "";
                    Room room = Room.builder()
                            .unitId(unitId)
                            .roomNumber(roomNumber)
                            .build();
                    roomRepository.save(room);
                }
            }
        }
        log.info("Created buildings/units/rooms for 翠湖花园 (8栋 × 3单元 × 4房)");

        // Create a second tenant for the dropdown
        Tenant tenant2 = Tenant.builder()
                .name("阳光花园")
                .build();
        tenant2 = tenantRepository.save(tenant2);
        log.info("Created tenant: 阳光花园");

        UUID t2Id = tenant2.getId();
        for (int b = 1; b <= 3; b++) {
            Building building = Building.builder()
                    .tenantId(t2Id)
                    .name(b + "栋")
                    .build();
            building = buildingRepository.save(building);
            UUID buildingId = building.getId();

            for (int u = 1; u <= 2; u++) {
                Unit unit = Unit.builder()
                        .buildingId(buildingId)
                        .name(u + "单元")
                        .build();
                unit = unitRepository.save(unit);
                UUID unitId = unit.getId();

                for (int r = 1; r <= 6; r++) {
                    String roomNumber = (b * 100 + r) + "";
                    Room room = Room.builder()
                            .unitId(unitId)
                            .roomNumber(roomNumber)
                            .build();
                    roomRepository.save(room);
                }
            }
        }
        log.info("Created buildings/units/rooms for 阳光花园 (3栋 × 2单元 × 6房)");
    }
}
