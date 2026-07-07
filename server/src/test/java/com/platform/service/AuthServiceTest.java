package com.platform.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private UUID userId;
    private User user;
    private String openid;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        openid = "wx_openid_test";
        token = "mock.jwt.token";

        user = User.builder()
                .id(userId)
                .openid(openid)
                .name("测试用户")
                .userType("业主")
                .authStatus("approved")
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }

    // ==================== wxLogin ====================

    @Test
    @DisplayName("微信登录 - 新用户自动注册并返回token和needRegister")
    void should_registerNewUser_when_wxLoginWithNewOpenid() {
        // Arrange
        WxLoginRequest req = new WxLoginRequest();
        req.setCode(openid);
        req.setName("新用户");

        when(userRepository.findByOpenid(openid)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });
        when(jwtTokenProvider.generateToken(userId.toString(), "业主")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.wxLogin(req);

        // Assert
        assertThat(result.get("token")).isEqualTo(token);
        assertThat(result.get("user")).isNotNull();
        assertThat(result.get("needRegister")).isEqualTo(true);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("微信登录 - 已注册用户直接返回token")
    void should_returnToken_when_userExists() {
        // Arrange
        WxLoginRequest req = new WxLoginRequest();
        req.setCode(openid);

        when(userRepository.findByOpenid(openid)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(userId.toString(), "业主")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.wxLogin(req);

        // Assert
        assertThat(result.get("token")).isEqualTo(token);
        assertThat(result.get("needRegister")).isNull();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("微信登录 - pending状态且无房间的用户触发重注册")
    void should_triggerReRegistration_when_pendingAndNoRoom() {
        // Arrange
        user.setAuthStatus("pending");
        user.setRoom(null);
        WxLoginRequest req = new WxLoginRequest();
        req.setCode(openid);

        when(userRepository.findByOpenid(openid)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateToken(userId.toString(), "业主")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.wxLogin(req);

        // Assert
        assertThat(result.get("needRegister")).isEqualTo(true);
        assertThat(user.getAuthStatus()).isEqualTo("registering");
    }

    @Test
    @DisplayName("微信登录 - 用户名null时默认使用'微信用户'")
    void should_useDefaultName_when_nameIsNull() {
        // Arrange
        WxLoginRequest req = new WxLoginRequest();
        req.setCode(openid);
        req.setName(null);

        when(userRepository.findByOpenid(openid)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            u.setName("微信用户");
            return u;
        });
        when(jwtTokenProvider.generateToken(userId.toString(), "业主")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.wxLogin(req);

        // Assert
        UserDTO dto = (UserDTO) result.get("user");
        assertThat(dto.getName()).isEqualTo("微信用户");
    }

    // ==================== adminLogin ====================

    @Test
    @DisplayName("管理员登录 - 正常登录成功返回token")
    void should_adminLogin_when_validCredentials() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        User adminUser = User.builder()
                .id(userId)
                .username("admin")
                .passwordHash("hashed_password")
                .name("管理员")
                .userType("admin")
                .authStatus("approved")
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("admin123", "hashed_password")).thenReturn(true);
        when(jwtTokenProvider.generateToken(userId.toString(), "admin")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.adminLogin(req);

        // Assert
        assertThat(result.get("token")).isEqualTo(token);
        assertThat(result.get("user")).isNotNull();
    }

    @Test
    @DisplayName("管理员登录 - 用户不存在时抛出异常")
    void should_throwException_when_usernameNotFound() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent");
        req.setPassword("password");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.adminLogin(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("账号或密码错误");
    }

    @Test
    @DisplayName("管理员登录 - 非管理员用户登录时抛出异常")
    void should_throwException_when_notAdmin() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("password");

        User normalUser = User.builder()
                .id(userId)
                .username("user1")
                .userType("业主")
                .build();

        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(normalUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.adminLogin(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("账号或密码错误");
    }

    @Test
    @DisplayName("管理员登录 - 密码不匹配时抛出异常")
    void should_throwException_when_passwordMismatch() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrongpassword");

        User adminUser = User.builder()
                .id(userId)
                .username("admin")
                .passwordHash("hashed_password")
                .userType("admin")
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrongpassword", "hashed_password")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.adminLogin(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("账号或密码错误");
    }

    @Test
    @DisplayName("管理员登录 - super_admin用户也可登录")
    void should_allowSuperAdminLogin() {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("pass");

        User superAdmin = User.builder()
                .id(userId)
                .username("superadmin")
                .passwordHash("hash")
                .userType("super_admin")
                .name("超级管理员")
                .build();

        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken(userId.toString(), "super_admin")).thenReturn(token);

        // Act
        Map<String, Object> result = authService.adminLogin(req);

        // Assert
        assertThat(result.get("token")).isEqualTo(token);
    }

    // ==================== register ====================

    @Test
    @DisplayName("用户注册 - 正常注册并解析房间信息")
    void should_registerUser_when_validInput() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        RegisterRequest req = new RegisterRequest();
        req.setTenantId(tenantId);
        req.setBuilding("3");
        req.setUnit("2");
        req.setRoom("1502");
        req.setName("张三");
        req.setPhone("13800138000");
        req.setUserType("业主");
        req.setDocImages(List.of("http://img1.jpg"));

        Building building = Building.builder().id(UUID.randomUUID()).tenantId(tenantId).name("3栋").build();
        Unit unit = Unit.builder().id(UUID.randomUUID()).buildingId(building.getId()).name("2单元").build();
        Room room = Room.builder().id(UUID.randomUUID()).unitId(unit.getId()).roomNumber("1502").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(buildingRepository.findByTenantIdAndName(tenantId, "3栋")).thenReturn(Optional.of(building));
        when(unitRepository.findByBuildingIdAndName(building.getId(), "2单元")).thenReturn(Optional.of(unit));
        when(roomRepository.findByUnitIdAndRoomNumber(unit.getId(), "1502")).thenReturn(Optional.of(room));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        UserDTO result = authService.register(req, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
        assertThat(user.getAuthStatus()).isEqualTo("pending");
        assertThat(user.getRoomId()).isEqualTo(room.getId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("用户注册 - 用户不存在时抛出异常")
    void should_throwException_when_userNotFound() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.register(req, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户不存在");
    }

    @Test
    @DisplayName("用户注册 - 房间信息不完整时抛出异常")
    void should_throwException_when_roomInfoIncomplete() {
        // Arrange
        RegisterRequest req = new RegisterRequest();
        req.setBuilding("3");
        // unit and room are null

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.register(req, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请完整填写小区、栋号、单元号和房号");
    }

    @Test
    @DisplayName("用户注册 - 创建新的Building/Unit/Room当不存在时")
    void should_createNewBuildingUnitRoom_when_notExist() {
        // Arrange
        UUID tenantId = UUID.randomUUID();
        RegisterRequest req = new RegisterRequest();
        req.setTenantId(tenantId);
        req.setBuilding("5");
        req.setUnit("1");
        req.setRoom("101");

        Building newBuilding = Building.builder().id(UUID.randomUUID()).tenantId(tenantId).name("5栋").build();
        Unit newUnit = Unit.builder().id(UUID.randomUUID()).buildingId(newBuilding.getId()).name("1单元").build();
        Room newRoom = Room.builder().id(UUID.randomUUID()).unitId(newUnit.getId()).roomNumber("101").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(buildingRepository.findByTenantIdAndName(tenantId, "5栋")).thenReturn(Optional.empty());
        when(buildingRepository.save(any(Building.class))).thenReturn(newBuilding);
        when(unitRepository.findByBuildingIdAndName(newBuilding.getId(), "1单元")).thenReturn(Optional.empty());
        when(unitRepository.save(any(Unit.class))).thenReturn(newUnit);
        when(roomRepository.findByUnitIdAndRoomNumber(newUnit.getId(), "101")).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenReturn(newRoom);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        UserDTO result = authService.register(req, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(user.getRoomId()).isEqualTo(newRoom.getId());
        verify(buildingRepository).save(any(Building.class));
        verify(unitRepository).save(any(Unit.class));
        verify(roomRepository).save(any(Room.class));
    }

    // ==================== getAuthStatus ====================

    @Test
    @DisplayName("获取认证状态 - 正常返回认证状态")
    void should_returnAuthStatus_when_userExists() {
        // Arrange
        user.setAuthStatus("pending");
        user.setRejectReason(null);
        user.setBannedReason(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        Map<String, Object> result = authService.getAuthStatus(userId);

        // Assert
        assertThat(result).containsEntry("authStatus", "pending");
        assertThat(result.get("rejectReason")).isNull();
    }

    @Test
    @DisplayName("获取认证状态 - 用户不存在时抛出异常")
    void should_throwException_when_userNotFoundInStatus() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.getAuthStatus(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户不存在");
    }

    // ==================== appeal ====================

    @Test
    @DisplayName("申诉 - banned用户申诉成功")
    void should_appeal_when_userIsBanned() {
        // Arrange
        user.setAuthStatus("banned");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        Map<String, Object> result = authService.appeal(userId);

        // Assert
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(user.getAuthStatus()).isEqualTo("pending");
        assertThat(user.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("申诉 - 非banned用户申诉时抛出异常")
    void should_throwException_when_userNotBanned() {
        // Arrange
        user.setAuthStatus("approved");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.appeal(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("当前状态不支持申诉");
    }

    // ==================== submitVerification ====================

    @Test
    @DisplayName("提交验证 - 正常提交实名验证")
    void should_submitVerification_when_validInput() {
        // Arrange
        VerificationSubmitRequest req = new VerificationSubmitRequest();
        req.setRealName("张三");
        req.setIdCard("110101199001011234");
        req.setIdCardFront("http://front.jpg");
        req.setIdCardBack("http://back.jpg");

        when(verificationRepository.save(any(Verification.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Map<String, Object> result = authService.submitVerification(userId, req);

        // Assert
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("验证信息已提交，请等待审核");
        verify(verificationRepository).save(any(Verification.class));
    }
}
