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

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOpenid(String openid);

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    Optional<User> findByPhoneAndTenantId(String phone, Long tenantId);

    Optional<User> findByPhoneAndTenantIdAndRoomId(String phone, Long tenantId, Long roomId);

    /** 注册房间查重按 (房间, 身份) 维度：同一房间业主与租客可并存 */
    Optional<User> findByRoomIdAndUserType(Long roomId, String userType);

    /** 鉴权探针：一次查询同时取 token 版本与审核状态 */
    interface AuthProbe {
        Integer getTokenVersion();
        String getAuthStatus();
    }

    @Query("SELECT u.tokenVersion AS tokenVersion, u.authStatus AS authStatus FROM User u WHERE u.id = ?1")
    AuthProbe findAuthProbeById(Long id);

    /** 显式 JOIN FETCH — 确保完整加载房间层级关系，用于显示名称格式化 */
    @Query("SELECT u FROM User u " +
           "LEFT JOIN FETCH u.room r " +
           "LEFT JOIN FETCH r.unit un " +
           "LEFT JOIN FETCH un.building b " +
           "WHERE u.id = :id")
    Optional<User> findByIdWithRoom(@Param("id") Long id);

    List<User> findByAuthStatus(String authStatus);

    @EntityGraph(attributePaths = {"room", "room.unit", "room.unit.building"})
    Page<User> findByAuthStatus(String authStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "room.unit", "room.unit.building"})
    Page<User> findByAuthStatusNot(String authStatus, Pageable pageable);

    /** 仅已审核通过的住户 — 排除 admin/super_admin 账号 */
    @EntityGraph(attributePaths = {"room", "room.unit", "room.unit.building"})
    Page<User> findByAuthStatusAndUserTypeNotIn(String authStatus, List<String> userTypes, Pageable pageable);

    long countByAuthStatus(String authStatus);

    long countByAuthStatusNot(String authStatus);

    long countByAuthStatusAndUserTypeNotIn(String authStatus, List<String> userTypes);

    List<User> findByRoomIdIn(List<Long> roomIds);

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

    List<User> findByTenantIdAndUserTypeIn(Long tenantId, List<String> userTypes);
}
