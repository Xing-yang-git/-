package com.platform.repository;

import com.platform.model.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByUnitId(UUID unitId);
    Optional<Room> findByUnitIdAndRoomNumber(UUID unitId, String roomNumber);
}
