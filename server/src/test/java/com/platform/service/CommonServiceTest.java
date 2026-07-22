package com.platform.service;

import com.platform.model.dto.BuildingDTO;
import com.platform.model.dto.RoomDTO;
import com.platform.model.dto.TenantDTO;
import com.platform.model.dto.UnitDTO;
import com.platform.model.entity.Building;
import com.platform.model.entity.Room;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.Unit;
import com.platform.repository.BuildingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommonService 单元测试")
class CommonServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private UnitRepository unitRepository;
    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private CommonService commonService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 注入一个临时上传目录，避免文件创建到工作目录
        ReflectionTestUtils.setField(commonService, "uploadDir", tempDir.toString());
    }

    // ==================== getAllTenants ====================

    @Test
    @DisplayName("获取所有租户 - 正常返回列表")
    void should_returnAllTenants() {
        // 准备
        Tenant t1 = Tenant.builder().id(1L).name("小区A").build();
        when(tenantRepository.findAll()).thenReturn(List.of(t1));

        // 执行
        List<TenantDTO> result = commonService.getAllTenants();

        // 断言
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("小区A");
    }

    // ==================== getBuildingsByTenantId ====================

    @Test
    @DisplayName("获取楼栋 - 正常委托查询")
    void should_returnBuildings_when_tenantHasBuildings() {
        // 准备
        Building b = Building.builder().id(1L).tenantId(10L).name("1栋").build();
        when(buildingRepository.findByTenantId(10L)).thenReturn(List.of(b));

        // 执行
        List<BuildingDTO> result = commonService.getBuildingsByTenantId(10L);

        // 断言
        assertThat(result).hasSize(1);
    }

    // ==================== getUnitsByBuildingId ====================

    @Test
    @DisplayName("获取单元 - 正常委托查询")
    void should_returnUnits_when_buildingHasUnits() {
        // 准备
        Unit u = Unit.builder().id(1L).buildingId(1L).name("1单元").build();
        when(unitRepository.findByBuildingId(1L)).thenReturn(List.of(u));

        // 执行
        List<UnitDTO> result = commonService.getUnitsByBuildingId(1L);

        // 断言
        assertThat(result).hasSize(1);
    }

    // ==================== getRoomsByUnitId ====================

    @Test
    @DisplayName("获取房间 - 正常委托查询")
    void should_returnRooms_when_unitHasRooms() {
        // 准备
        Room r = Room.builder().id(1L).unitId(1L).roomNumber("101").build();
        when(roomRepository.findByUnitId(1L)).thenReturn(List.of(r));

        // 执行
        List<RoomDTO> result = commonService.getRoomsByUnitId(1L);

        // 断言
        assertThat(result).hasSize(1);
    }

    // ==================== uploadFile — 魔数测试素材 ====================

    /** JPEG 文件头：FF D8 FF */
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    /** PNG 文件头：89 'P' 'N' 'G' */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    /** GIF 文件头："GIF89a" */
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};
    /** WEBP 文件头："RIFF" + 4 字节长度 + "WEBP" */
    private static final byte[] WEBP_MAGIC = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    // ==================== uploadFile — 成功路径 ====================

    @Test
    @DisplayName("上传文件 - jpg 魔数上传成功")
    void should_upload_when_jpgFile() throws Exception {
        // 准备
        MultipartFile file = mockFile("photo.jpg", "image/jpeg", JPEG_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).startsWith("/uploads/");
        assertThat(result).endsWith(".jpg");
        verify(file).transferTo(any(java.io.File.class));
    }

    @Test
    @DisplayName("上传文件 - png 魔数上传成功")
    void should_upload_when_pngFile() throws Exception {
        // 准备
        MultipartFile file = mockFile("screenshot.png", "image/png", PNG_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).endsWith(".png");
    }

    @Test
    @DisplayName("上传文件 - gif 魔数上传成功")
    void should_upload_when_gifFile() throws Exception {
        // 准备
        MultipartFile file = mockFile("anim.gif", "image/gif", GIF_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).endsWith(".gif");
    }

    @Test
    @DisplayName("上传文件 - webp 魔数上传成功")
    void should_upload_when_webpFile() throws Exception {
        // 准备
        MultipartFile file = mockFile("img.webp", "image/webp", WEBP_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).endsWith(".webp");
    }

    // ==================== uploadFile — wx 真机场景回归 ====================

    @Test
    @DisplayName("上传文件 - 无扩展名 + octet-stream 但内容是 jpg（wx 真机临时文件）上传成功")
    void should_upload_when_noExtensionButJpegContent() throws Exception {
        // 准备：wx.uploadFile 真机临时文件常无扩展名、Content-Type 为 application/octet-stream，
        // 曾因此被扩展名白名单误拒（注册上传证件照 500），魔数校验必须放行
        MultipartFile file = mockFile("compressed_img_tmp_123456", "application/octet-stream", JPEG_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言：存盘扩展名由魔数生成
        assertThat(result).endsWith(".jpg");
    }

    @Test
    @DisplayName("上传文件 - originalFilename 为 null 但内容是 png 时上传成功")
    void should_upload_when_nullFilenameButPngContent() throws Exception {
        // 准备
        MultipartFile file = mockFile(null, null, PNG_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).endsWith(".png");
    }

    @Test
    @DisplayName("上传文件 - 扩展名与内容不符时以魔数为准")
    void should_useMagicDetectedExtension_when_filenameLies() throws Exception {
        // 准备：文件名声称 JPG，内容实为 PNG——存盘扩展名必须跟随真实内容
        MultipartFile file = mockFile("PHOTO.JPG", "image/jpeg", PNG_MAGIC);

        // 执行
        String result = commonService.uploadFile(file);

        // 断言
        assertThat(result).endsWith(".png");
    }

    // ==================== uploadFile — 空文件拒绝 ====================

    @Test
    @DisplayName("上传文件 - 空文件拒绝")
    void should_reject_when_fileEmpty() {
        // 准备
        MultipartFile file = mockEmptyFile();

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件为空");
    }

    // ==================== uploadFile — 非图片内容拒绝（防存储型 XSS） ====================

    @Test
    @DisplayName("上传文件 - html 内容拒绝")
    void should_reject_when_htmlFile() {
        // 准备
        MultipartFile file = mockFile("evil.html", "text/html", "<html><script>".getBytes());

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("仅支持上传 jpg/jpeg/png/gif/webp 图片");
    }

    @Test
    @DisplayName("上传文件 - 伪装成 png 扩展名的 html 内容拒绝")
    void should_reject_when_htmlDisguisedAsPng() {
        // 准备：扩展名白名单挡不住这种伪装，魔数校验必须挡住
        MultipartFile file = mockFile("img.png", "image/png", "<svg onload=alert(1)>".getBytes());

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("仅支持上传 jpg/jpeg/png/gif/webp 图片");
    }

    @Test
    @DisplayName("上传文件 - exe 内容拒绝")
    void should_reject_when_exeFile() {
        // 准备：PE 可执行文件头 "MZ"
        MultipartFile file = mockFile("virus.exe", "application/octet-stream",
                new byte[]{'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("仅支持上传 jpg/jpeg/png/gif/webp 图片");
    }

    @Test
    @DisplayName("上传文件 - pdf 内容伪装 jpg 扩展名拒绝")
    void should_reject_when_pdfDisguisedAsJpg() {
        // 准备：PDF 文件头 "%PDF"
        MultipartFile file = mockFile("doc.jpg", "image/jpeg", "%PDF-1.7 ...".getBytes());

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("仅支持上传 jpg/jpeg/png/gif/webp 图片");
    }

    @Test
    @DisplayName("上传文件 - 内容过短无法识别时拒绝")
    void should_reject_when_contentTooShort() {
        // 准备：只有 2 字节，凑不齐任何图片魔数
        MultipartFile file = mockFile("tiny.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});

        // 执行 & 断言
        assertThatThrownBy(() -> commonService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("仅支持上传 jpg/jpeg/png/gif/webp 图片");
    }

    // ==================== 空列表查询 ====================

    @Test
    @DisplayName("getAllTenants - 无租户时返回空列表")
    void should_returnEmpty_when_noTenants() {
        // 准备
        when(tenantRepository.findAll()).thenReturn(Collections.emptyList());

        // 执行
        List<TenantDTO> result = commonService.getAllTenants();

        // 断言
        assertThat(result).isEmpty();
    }

    // ==================== 辅助方法 ====================

    private MultipartFile mockFile(String originalFilename, String contentType, byte[] bytes) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getOriginalFilename()).thenReturn(originalFilename);
        lenient().when(file.getContentType()).thenReturn(contentType);
        // getInputStream 供 transferTo 内部使用
        try {
            lenient().when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception ignored) {
        }
        lenient().when(file.getSize()).thenReturn((long) bytes.length);
        return file;
    }

    private MultipartFile mockEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(true);
        return file;
    }
}
