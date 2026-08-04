package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.PolishingClient;
import com.platform.common.BizStatus;
import com.platform.common.PostType;
import com.platform.model.dto.BorrowResponseDTO;
import com.platform.model.dto.IdleItemDTO;
import com.platform.model.dto.PageDTO;
import com.platform.service.BorrowService;
import com.platform.service.IdleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentToolDispatcher 读工具执行器单元测试 — 覆盖工具 JSON 输出与 postType 归一化。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentToolDispatcher 读工具执行器单元测试")
class AgentToolDispatcherTest {

    @Mock
    private IdleService idleService;
    @Mock
    private BorrowService borrowService;
    @Mock
    private PolishingClient polishingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AgentToolDispatcher(idleService, borrowService, polishingClient, objectMapper);
    }

    @Test
    @DisplayName("search_items - 返回物品摘要 JSON，缺失字段补空串")
    void should_searchItems_returnSummaryJson() throws Exception {
        IdleItemDTO dto = IdleItemDTO.builder()
                .id(1L).title("博世冲击钻").description(null).category(null).build();
        PageDTO<IdleItemDTO> page = PageDTO.<IdleItemDTO>builder()
                .content(List.of(dto)).totalElements(1).build();
        when(idleService.search(eq(1L), eq("钻"), eq(PostType.LEND), eq(0), eq(5), eq("hybrid")))
                .thenReturn(page);

        String result = dispatcher.searchItems(1L, new SearchParams("钻", null));

        var list = objectMapper.readTree(result);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(1L);
        assertThat(list.get(0).get("title").asText()).isEqualTo("博世冲击钻");
        assertThat(list.get(0).get("description").asText()).isEmpty();
        assertThat(list.get(0).get("category").asText()).isEmpty();
    }

    @Test
    @DisplayName("search_items - 中文别名 postType 归一化为枚举值")
    void should_normalizePostType_when_chineseAlias() {
        PageDTO<IdleItemDTO> page = PageDTO.<IdleItemDTO>builder().content(List.of()).totalElements(0).build();
        when(idleService.search(eq(1L), eq("梯子"), eq(PostType.WANTED), eq(0), eq(5), eq("hybrid")))
                .thenReturn(page);

        dispatcher.searchItems(1L, new SearchParams("梯子", "求借"));

        verify(idleService).search(eq(1L), eq("梯子"), eq(PostType.WANTED), eq(0), eq(5), eq("hybrid"));
    }

    @Test
    @DisplayName("my_posts - 返回我的发布摘要 JSON")
    void should_myPosts_returnPostsJson() throws Exception {
        IdleItemDTO dto = IdleItemDTO.builder()
                .id(3L).title("电饭煲").status(BizStatus.ONLINE).build();
        when(idleService.getMyPosts(1L, PostType.LEND)).thenReturn(List.of(dto));

        String result = dispatcher.myPosts(1L, new MyPostsParams(null));

        var list = objectMapper.readTree(result);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(3L);
        assertThat(list.get(0).get("status").asText()).isEqualTo(BizStatus.ONLINE);
    }

    @Test
    @DisplayName("my_borrows_due - 仅返回进行中（active/approved）的借用")
    void should_myBorrowsDue_filterActiveOnly() throws Exception {
        BorrowResponseDTO active = BorrowResponseDTO.builder()
                .idleId(1L).idleTitle("电钻").durationDays(3).status(BizStatus.ACTIVE).build();
        BorrowResponseDTO approved = BorrowResponseDTO.builder()
                .idleId(2L).idleTitle("梯子").durationDays(null).status(BizStatus.APPROVED).build();
        BorrowResponseDTO returned = BorrowResponseDTO.builder()
                .idleId(3L).idleTitle("锤子").durationDays(1).status(BizStatus.RETURNED).build();
        BorrowResponseDTO pending = BorrowResponseDTO.builder()
                .idleId(4L).idleTitle("扳手").durationDays(1).status(BizStatus.PENDING).build();
        when(borrowService.getMyApplications(1L))
                .thenReturn(List.of(active, approved, returned, pending));

        String result = dispatcher.myBorrowsDue(1L);

        var list = objectMapper.readTree(result);
        assertThat(list).hasSize(2);
        // 仅 active/approved 进入结果
        assertThat(list.get(0).get("idleId").asLong()).isEqualTo(1L);
        assertThat(list.get(1).get("idleId").asLong()).isEqualTo(2L);
        // durationDays 缺失时补 0
        assertThat(list.get(1).get("durationDays").asInt()).isZero();
    }

    @Test
    @DisplayName("generate_feedback - 角色为空时默认借出方，JSON 包裹返回")
    void should_generateFeedback_defaultRoleLend() throws Exception {
        when(polishingClient.generateFeedback(PolishingClient.ROLE_LEND, "电钻", "借用顺利"))
                .thenReturn("邻居把电钻借给我用，用起来很顺手，归还也顺利，感谢！");

        String result = dispatcher.generateFeedback(1L, new FeedbackParams(null, "电钻", "借用顺利"));

        var json = objectMapper.readTree(result);
        assertThat(json.get("feedback").asText()).contains("感谢");
    }

    @Test
    @DisplayName("generate_feedback - 未知 postType 映射到 LEND")
    void should_normalizeUnknownPostType_toLend() {
        PageDTO<IdleItemDTO> page = PageDTO.<IdleItemDTO>builder().content(List.of()).totalElements(0).build();
        when(idleService.search(eq(1L), anyString(), eq(PostType.LEND), eq(0), eq(5), eq("hybrid")))
                .thenReturn(page);

        // "出借"/"借出"/未知值均不在别名映射中，统一兜底为 LEND
        dispatcher.searchItems(1L, new SearchParams("伞", "出借"));
        dispatcher.searchItems(1L, new SearchParams("伞", "借出"));
        dispatcher.searchItems(1L, new SearchParams("伞", "其他类型"));

        verify(idleService, org.mockito.Mockito.times(3))
                .search(eq(1L), anyString(), eq(PostType.LEND), eq(0), eq(5), eq("hybrid"));
    }

    @Test
    @DisplayName("search_items - 非 LEND 英文枚举原样透传")
    void should_passThroughEnglishEnum_when_valid() {
        PageDTO<IdleItemDTO> page = PageDTO.<IdleItemDTO>builder().content(List.of()).totalElements(0).build();
        when(idleService.search(eq(1L), eq("帮忙"), eq(PostType.HELP), eq(0), eq(5), eq("hybrid")))
                .thenReturn(page);

        dispatcher.searchItems(1L, new SearchParams("帮忙", "HELP"));

        verify(idleService).search(eq(1L), eq("帮忙"), eq(PostType.HELP), eq(0), eq(5), eq("hybrid"));
    }
}
