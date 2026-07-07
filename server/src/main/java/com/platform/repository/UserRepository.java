package com.platform.repository;

import com.platform.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByOpenid(String openid);

    Optional<User> findByUsername(String username);

    List<User> findByAuthStatus(String authStatus);

    @EntityGraph(attributePaths = {"room", "room.unit", "room.unit.building"})
    Page<User> findByAuthStatus(String authStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "room.unit", "room.unit.building"})
    Page<User> findByAuthStatusNot(String authStatus, Pageable pageable);

    long countByAuthStatus(String authStatus);

    long countByAuthStatusNot(String authStatus);

    List<User> findByRoomIdIn(List<UUID> roomIds);

    @Query("SELECT u FROM User u JOIN u.room r JOIN r.unit un JOIN un.building b " +
           "WHERE u.authStatus = 'approved' " +
           "AND (:building IS NULL OR b.name LIKE %:building%) " +
           "AND (:unit IS NULL OR un.name LIKE %:unit%) " +
           "AND (:room IS NULL OR r.roomNumber LIKE %:room%) " +
           "AND (:userType IS NULL OR u.userType = :userType) " +
           "AND (:keyword IS NULL OR u.name LIKE %:keyword% OR u.phone LIKE %:keyword%)")
    Page<User> findResidents(@Param("building") String building,
                             @Param("unit") String unit,
                             @Param("room") String room,
                             @Param("userType") String userType,
                             @Param("keyword") String keyword,
                             Pageable pageable);
}
