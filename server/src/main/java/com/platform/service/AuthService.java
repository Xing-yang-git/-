package com.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.model.dto.LoginRequest;
import com.platform.model.dto.PhoneLoginRequest;
import com.platform.model.dto.RegisterRequest;
import com.platform.model.dto.UserDTO;
import com.platform.model.dto.WxLoginRequest;
import com.platform.model.entity.Building;
import com.platform.model.entity.Room;
import com.platform.model.entity.Unit;
import com.platform.model.entity.User;
import com.platform.repository.BuildingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import com.platform.repository.UserRepository;
import com.platform.security.JwtTokenProvider;
import com.platform.websocket.ChatWebSocketHandler;
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

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final WeChatService weChatService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public AuthService(UserRepository userRepository,
                       RoomRepository roomRepository,
                       BuildingRepository buildingRepository,
                       UnitRepository unitRepository,
                       TenantRepository tenantRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       WeChatService weChatService,
                       ChatWebSocketHandler chatWebSocketHandler) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.tenantRepository = tenantRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.weChatService = weChatService;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    /** C端发新令牌：版本+1 使其他设备旧 token 失效，并主动踢掉其在线 WS 连接 */
    private String issueUserToken(User user) {
        int newVer = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(newVer);
        userRepository.save(user);
        chatWebSocketHandler.closeUserSessions(user.getId().toString());
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getUserType(), newVer);
    }

    @Transactional
    public Map<String, Object> wxLogin(WxLoginRequest req) {
        // 真机: code → 微信 API → 稳定 openid;  本地开发: 未配 AppID 时 code 即 openid
        String openid = weChatService.code2Session(req.getCode());
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
            // 历史数据修复：用户被创建为 pending 状态但从未完成注册
            user.setAuthStatus("registering");
            user = userRepository.save(user);
            needRegister = true;
        } else if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            // 用户已用手机号完成注册——不再跳转注册页
            // 除非其明确处于 'registering' 状态
            if (!"registering".equals(user.getAuthStatus())) {
                needRegister = false;
            }
        }

        String token = issueUserToken(user);
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

    public Map<String, Object> phoneLogin(PhoneLoginRequest req) {
        if (req.getTenantId() == null) {
            throw new RuntimeException("请选择小区");
        }
        User user = userRepository.findByPhoneAndTenantId(req.getPhone(), req.getTenantId())
                .orElseThrow(() -> new RuntimeException("手机号未注册"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new RuntimeException("该账号未设置密码，请使用微信登录");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        if ("banned".equals(user.getAuthStatus())) {
            String reason = user.getBannedReason() != null ? user.getBannedReason() : "如有疑问请联系物业";
            throw new RuntimeException("账号已被封禁：" + reason);
        }

        String token = issueUserToken(user);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toDTO(user));
        return result;
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest req, Long userId) {
        // ── 先解析房间（唯一性校验需要用到）──
        Room room = resolveRoom(req.getTenantId(), req.getBuilding(), req.getUnit(), req.getRoom());

        // ── 唯一性校验：小区内手机号 + 房间 ──
        if (req.getPhone() != null && !req.getPhone().isEmpty() && req.getTenantId() != null) {
            // 校验 1：手机号是否已在本小区注册？
            userRepository.findByPhoneAndTenantId(req.getPhone(), req.getTenantId())
                    .ifPresent(existing -> {
                        if (userId != null && existing.getId().equals(userId)) return;
                        throw new RuntimeException("该手机号已在本小区注册");
                    });

            // 校验 2：房间是否已被注册？
            userRepository.findByRoomId(room.getId())
                    .ifPresent(existing -> {
                        if (userId != null && existing.getId().equals(userId)) return;
                        throw new RuntimeException("该住户已被注册");
                    });
        }

        // ── 获取或创建用户 ──
        User user;
        if (userId != null) {
            // 已有流程：wxLogin 预先创建的用户
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
        } else {
            // 公开注册：创建新用户
            user = new User();
            user.setCreatedAt(LocalDateTime.now());
        }

        user.setName(req.getName());
        user.setPhone(req.getPhone());
        user.setTenantId(req.getTenantId());

        // 注册时若提供了密码则设置
        if (req.getPassword() != null && !req.getPassword().isEmpty()) {
            if (req.getPassword().length() < 8 || req.getPassword().length() > 20) {
                throw new RuntimeException("密码长度需要8-20位");
            }
            if (!req.getPassword().matches(".*[a-zA-Z].*") || !req.getPassword().matches(".*[0-9].*")) {
                throw new RuntimeException("密码需要包含字母和数字");
            }
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }

        if (req.getUserType() != null) {
            user.setUserType(mapUserType(req.getUserType()));
        }
        user.setAuthStatus("pending");
        user.setRejectReason(null);

        // 将 docImages 以 JSON 数组形式存储
        if (req.getDocImages() != null && !req.getDocImages().isEmpty()) {
            try {
                user.setDocImages(objectMapper.writeValueAsString(req.getDocImages()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize docImages: {}", e.getMessage());
            }
        }

        user.setRoomId(room.getId());
        user.setRoom(room);

        user = userRepository.save(user);

        // 返回 token，使客户端可立即完成认证
        String token = issueUserToken(user);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toDTO(user));
        return result;
    }

    /**
     * 根据 楼栋/单元/房号 的文本输入解析房间。
     * 若 Building、Unit、Room 实体不存在则自动创建。
     */
    /**
     * 将前端用户类型编码映射为数据库 CHECK 约束值。
     * 数据库约束：user_type IN ('业主','租客','物业','admin','super_admin')
     */
    private String mapUserType(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "owner"  -> "业主";
            case "tenant" -> "租客";
            case "物业"   -> "物业";
            default       -> raw;  // admin / super_admin / 已是中文的值直接透传
        };
    }

    private Room resolveRoom(Long tenantId, String buildingText, String unitText, String roomText) {
        if (tenantId == null || buildingText == null || unitText == null || roomText == null) {
            throw new RuntimeException("请完整填写小区、栋号、单元号和房号");
        }

        String buildingName = buildingText + "栋";
        String unitName = unitText + "单元";

        // 查找或创建楼栋
        Building building = buildingRepository.findByTenantIdAndName(tenantId, buildingName)
                .orElseGet(() -> buildingRepository.save(
                        Building.builder()
                                .tenantId(tenantId)
                                .name(buildingName)
                                .build()));

        // 查找或创建单元
        Unit unit = unitRepository.findByBuildingIdAndName(building.getId(), unitName)
                .orElseGet(() -> unitRepository.save(
                        Unit.builder()
                                .buildingId(building.getId())
                                .name(unitName)
                                .build()));

        // 查找或创建房间
        return roomRepository.findByUnitIdAndRoomNumber(unit.getId(), roomText)
                .orElseGet(() -> roomRepository.save(
                        Room.builder()
                                .unitId(unit.getId())
                                .roomNumber(roomText)
                                .build()));
    }

    public Map<String, Object> getAuthStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Map<String, Object> result = new HashMap<>();
        result.put("authStatus", user.getAuthStatus());
        result.put("rejectReason", user.getRejectReason());
        result.put("bannedReason", user.getBannedReason());
        return result;
    }

    @Transactional
    public Map<String, Object> appeal(Long userId) {
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
                .tenantId(user.getTenantId())
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
                Long tenantId = user.getRoom().getUnit().getBuilding().getTenantId();
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
