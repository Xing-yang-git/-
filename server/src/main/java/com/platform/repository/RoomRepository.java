package com.platform.repository;

import com.platform.model.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByUnitId(Long unitId);
    Optional<Room> findByUnitIdAndRoomNumber(Long unitId, String roomNumber);
}
