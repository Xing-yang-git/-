package com.platform.ai.matching;

import com.platform.ai.embedding.EmbeddingService;
import com.platform.common.NotificationType;
import com.platform.common.PostType;
import com.platform.config.AiConfig;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.User;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.NotificationRepository;
import com.platform.repository.UserRepository;
import com.platform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MatchingService 供需匹配单元测试 — 覆盖匹配通知生成与各类去重。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingService 供需匹配单元测试")
class MatchingServiceTest {

    @Mock
    private IdleItemRepository idleItemRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiConfig aiConfig;

    private MatchingService service;

    private static final Long WANTED_ITEM_ID = 100L;
    private static final Long WANTED_USER_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new MatchingService(idleItemRepository, notificationService,
                notificationRepository, embeddingService, userRepository, aiConfig);
    }

    private IdleItem wantedItem(String title) {
        return IdleItem.builder()
                .id(WANTED_ITEM_ID).userId(WANTED_USER_ID).tenantId(1L)
                .postType(PostType.WANTED).title(title).embedding("[0.1,0.2]").build();
    }

    private Object[] row(Long lendItemId, Long lenderUserId) {
        return new Object[]{lendItemId, "类似物品", lenderUserId, 0.1};
    }

    @Test
    @DisplayName("匹配 - 非 WANTED 类型直接跳过")
    void should_skip_when_notWanted() {
        IdleItem item = IdleItem.builder().id(1L).postType(PostType.LEND).build();

        service.matchWantedToLend(item);

        verify(idleItemRepository, never()).findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("匹配 - 无语义向量先生成，再匹配")
    void should_generateEmbedding_when_missing() {
        IdleItem item = IdleItem.builder().id(WANTED_ITEM_ID).userId(WANTED_USER_ID).tenantId(1L)
                .postType(PostType.WANTED).title("电钻").build();
        doAnswer(inv -> {
            ((IdleItem) inv.getArgument(0)).setEmbedding("[0.1,0.2]");
            return null;
        }).when(embeddingService).updateItemEmbedding(item);
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.matchWantedToLend(item);

        verify(embeddingService).updateItemEmbedding(item);
        verify(idleItemRepository).findSimilarByEmbedding(eq("[0.1,0.2]"), eq(1L), eq(PostType.LEND),
                eq(WANTED_USER_ID), any(), any(), anyDouble(), eq(6));
    }

    @Test
    @DisplayName("匹配 - 向量生成异常时终止匹配")
    void should_stop_when_embeddingGenerationThrows() {
        IdleItem item = IdleItem.builder().id(WANTED_ITEM_ID).userId(WANTED_USER_ID).tenantId(1L)
                .postType(PostType.WANTED).title("电钻").build();
        org.mockito.Mockito.doThrow(new RuntimeException("API down"))
                .when(embeddingService).updateItemEmbedding(item);

        service.matchWantedToLend(item);

        verify(idleItemRepository, never()).findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("匹配 - 无相似结果时不发通知")
    void should_notNotify_when_noMatch() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of());

        service.matchWantedToLend(wantedItem("电钻"));

        verify(notificationService, never()).create(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("匹配 - 命中多条时向每个出借方发通知")
    void should_notifyLenders_when_matched() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(row(101L, 200L), row(102L, 201L)));
        when(userRepository.findById(WANTED_USER_ID))
                .thenReturn(Optional.of(User.builder().id(WANTED_USER_ID).name("张三").build()));
        when(notificationRepository.existsByUserIdAndTypeAndRelatedId(anyLong(), any(), anyLong()))
                .thenReturn(false);

        service.matchWantedToLend(wantedItem("电钻"));

        ArgumentCaptor<Long> lenderCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2))
                .create(lenderCaptor.capture(), org.mockito.ArgumentMatchers.eq(NotificationType.MATCH_DEMAND),
                        any(), contentCaptor.capture(), org.mockito.ArgumentMatchers.eq(WANTED_ITEM_ID));
        assertThat(lenderCaptor.getAllValues()).containsExactly(200L, 201L);
        assertThat(contentCaptor.getAllValues().get(0)).contains("张三").contains("电钻");
    }

    @Test
    @DisplayName("匹配 - 同批次内同一出借方只通知一次")
    void should_dedupByBatch_when_sameLender() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(row(101L, 200L), row(102L, 200L)));
        when(userRepository.findById(WANTED_USER_ID))
                .thenReturn(Optional.of(User.builder().id(WANTED_USER_ID).name("张三").build()));
        when(notificationRepository.existsByUserIdAndTypeAndRelatedId(anyLong(), any(), anyLong()))
                .thenReturn(false);

        service.matchWantedToLend(wantedItem("电钻"));

        verify(notificationService, times(1)).create(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("匹配 - 已通知过的持久去重，不再重复通知")
    void should_dedupPersistently_when_alreadyNotified() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(row(101L, 200L)));
        when(notificationRepository.existsByUserIdAndTypeAndRelatedId(200L, NotificationType.MATCH_DEMAND, WANTED_ITEM_ID))
                .thenReturn(true);

        service.matchWantedToLend(wantedItem("电钻"));

        verify(notificationService, never()).create(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("匹配 - 数据异常的行跳过")
    void should_skip_when_malformedRow() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(new Object[]{null, "标题", 200L, 0.1}));

        service.matchWantedToLend(wantedItem("电钻"));

        verify(notificationService, never()).create(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("匹配 - 求借方不存在时通知文案用「未知用户」")
    void should_useUnknownUser_when_wantedUserNotFound() {
        when(idleItemRepository.findSimilarByEmbedding(any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.<Object[]>of(row(101L, 200L)));
        when(userRepository.findById(WANTED_USER_ID)).thenReturn(Optional.empty());
        when(notificationRepository.existsByUserIdAndTypeAndRelatedId(anyLong(), any(), anyLong()))
                .thenReturn(false);

        service.matchWantedToLend(wantedItem("电钻"));

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).create(anyLong(), any(), any(), contentCaptor.capture(), anyLong());
        assertThat(contentCaptor.getValue()).contains("未知用户");
    }
}
