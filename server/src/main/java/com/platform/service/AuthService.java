package com.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.model.dto.LoginRequest;
import com.platform.model.dto.RegisterRequest;
import com.platform.model.dto.UserDTO;
import com.platform.model.dto.VerificationSubmitRequest;
import com.platform.model.dto.WxLoginRequest;
import com.platform.model.entity.Building;
import com.platform.model.entity.Room;
import com.platform.model.entity.Unit;
import com.platform.model.entity.User;
import com.platform.model.entity.Verification;
import com.platform.repository.BuildingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import com.platform.repository.UserRepository;
import com.platform.repository.VerificationRepository;
import com.platform.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;
    private final RoomRepository roomRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       VerificationRepository verificationRepository,
                       RoomRepository roomRepository,
                       BuildingRepository buildingRepository,
                       UnitRepository unitRepository,
                       TenantRepository tenantRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.roomRepository = roomRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.tenantRepository = tenantRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> wxLogin(WxLoginRequest req) {
        String openid = req.getCode();
        User user = userRepository.findByOpenid(openid).orElse(null);
        boolean needRegister = false;

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setName(req.getName() != null ? req.getName() : "微信用户");
            user.setUserType("业主");
            user.setAuthStatus("registering");
            user.setCreatedAt(LocalDateTime.now());
            user = userRepository.save(user);
            needRegister = true;
        } else if ("pending".equals(user.getAuthStatus()) && user.getRoom() == null) {
            // Legacy fix: user was created with pending status but never registered
            user.setAuthStatus("registering");
            user = userRepository.save(user);
            needRegister = true;
        }

        String token = jwtTokenProvider.generateToken(user.getId().toString(), user.getUserType());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toDTO(user));
        if (needRegister) {
            result.put("needRegister", true);
        }
        return result;
    }

    public Map<String, Object> adminLogin(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("账号或密码错误"));

        if (!"admin".equals(user.getUserType()) && !"super_admin".equals(user.getUserType())) {
            throw new RuntimeException("账号或密码错误");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("账号或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId().toString(), user.getUserType());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toDTO(user));
        return result;
    }

    @Transactional
    public UserDTO register(RegisterRequest req, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setName(req.getName());
        user.setPhone(req.getPhone());
        if (req.getUserType() != null) {
            user.setUserType(req.getUserType());
        }
        user.setAuthStatus("pending");
        user.setRejectReason(null);

        // Store docImages as JSON array
        if (req.getDocImages() != null && !req.getDocImages().isEmpty()) {
            try {
                user.setDocImages(objectMapper.writeValueAsString(req.getDocImages()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize docImages: {}", e.getMessage());
            }
        }

        // Resolve or create the room from building/unit/room text
        Room room = resolveRoom(req.getTenantId(), req.getBuilding(), req.getUnit(), req.getRoom());
        user.setRoomId(room.getId());
        user.setRoom(room);

        user = userRepository.save(user);
        return toDTO(user);
    }

    /**
     * Resolve a room UUID from building/unit/room text input.
     * Creates Building, Unit, and Room entities if they don't exist.
     */
    private Room resolveRoom(UUID tenantId, String buildingText, String unitText, String roomText) {
        if (tenantId == null || buildingText == null || unitText == null || roomText == null) {
            throw new RuntimeException("请完整填写小区、栋号、单元号和房号");
        }

        String buildingName = buildingText + "栋";
        String unitName = unitText + "单元";

        // Find or create building
        Building building = buildingRepository.findByTenantIdAndName(tenantId, buildingName)
                .orElseGet(() -> buildingRepository.save(
                        Building.builder()
                                .tenantId(tenantId)
                                .name(buildingName)
                                .build()));

        // Find or create unit
        Unit unit = unitRepository.findByBuildingIdAndName(building.getId(), unitName)
                .orElseGet(() -> unitRepository.save(
                        Unit.builder()
                                .buildingId(building.getId())
                                .name(unitName)
                                .build()));

        // Find or create room
        return roomRepository.findByUnitIdAndRoomNumber(unit.getId(), roomText)
                .orElseGet(() -> roomRepository.save(
                        Room.builder()
                                .unitId(unit.getId())
                                .roomNumber(roomText)
                                .build()));
    }

    public Map<String, Object> getAuthStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Map<String, Object> result = new HashMap<>();
        result.put("authStatus", user.getAuthStatus());
        result.put("rejectReason", user.getRejectReason());
        result.put("bannedReason", user.getBannedReason());
        return result;
    }

    @Transactional
    public Map<String, Object> appeal(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!"banned".equals(user.getAuthStatus())) {
            throw new RuntimeException("当前状态不支持申诉");
        }

        user.setAuthStatus("pending");
        user.setRejectReason(null);
        userRepository.save(user);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "申诉已提交，请等待物业审核");
        return result;
    }

    @Transactional
    public Map<String, Object> submitVerification(UUID userId, VerificationSubmitRequest req) {
        Verification verification = new Verification();
        verification.setUserId(userId);
        verification.setRealName(req.getRealName());
        verification.setIdCard(req.getIdCard());
        verification.setIdCardFront(req.getIdCardFront());
        verification.setIdCardBack(req.getIdCardBack());
        verification.setStatus("pending");
        verification.setCreatedAt(LocalDateTime.now());
        verificationRepository.save(verification);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "验证信息已提交，请等待审核");
        return result;
    }

    private UserDTO toDTO(User user) {
        List<String> docImages = Collections.emptyList();
        if (user.getDocImages() != null && !user.getDocImages().isEmpty()) {
            try {
                docImages = objectMapper.readValue(user.getDocImages(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.debug("Failed to parse docImages JSON: {}", e.getMessage());
            }
        }

        return UserDTO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .username(user.getUsername())
                .name(user.getName())
                .phone(user.getPhone())
                .userType(user.getUserType())
                .authStatus(user.getAuthStatus())
                .roomId(user.getRoom() != null ? user.getRoom().getId() : null)
                .userRoom(formatRoom(user))
                .tenantName(resolveTenantName(user))
                .docImages(docImages)
                .rejectReason(user.getRejectReason())
                .bannedReason(user.getBannedReason())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String formatRoom(User user) {
        if (user.getRoom() == null) {
            return "";
        }
        try {
            String buildingName = user.getRoom().getUnit() != null
                    && user.getRoom().getUnit().getBuilding() != null
                    ? user.getRoom().getUnit().getBuilding().getName() : "";
            String unitName = user.getRoom().getUnit() != null
                    ? user.getRoom().getUnit().getName() : "";
            String roomNumber = user.getRoom().getRoomNumber() != null
                    ? user.getRoom().getRoomNumber() : "";
            return buildingName + unitName + roomNumber + "号";
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveTenantName(User user) {
        try {
            if (user.getRoom() != null
                    && user.getRoom().getUnit() != null
                    && user.getRoom().getUnit().getBuilding() != null) {
                UUID tenantId = user.getRoom().getUnit().getBuilding().getTenantId();
                if (tenantId != null) {
                    return tenantRepository.findById(tenantId)
                            .map(t -> t.getName())
                            .orElse("");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve tenant name: {}", e.getMessage());
        }
        return "";
    }
}
