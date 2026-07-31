package com.platform.ai.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.BizStatus;
import com.platform.common.ModerationStatus;
import com.platform.common.NotificationType;
import com.platform.model.entity.HelpRequest;
import com.platform.model.entity.IdleItem;
import com.platform.repository.HelpRequestRepository;
import com.platform.repository.IdleItemRepository;
import com.platform.service.NotificationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI 内容审核服务 — 异步审核闲置物品和求助信息，三级分流处理。
 *
 * <p>审核流程：
 * <ol>
 *   <li>新发布内容状态设为 pending_review，审核状态为 pending</li>
 *   <li>异步调用 GLM-4V-Flash 审核图片、GLM-4-Flash 审核文本</li>
 *   <li>汇总所有审核结果，取最严重等级：green（放行）/ yellow（待复核）/ red（驳回）</li>
 *   <li>green → 自动上线；yellow → 保持 pending_review 等待管理员复核；red → 下线并通知用户</li>
 * </ol>
 *
 * <p>并发控制：使用固定大小为 2 的线程池，避免过度占用 API 配额。</p>
 */
@Slf4j
@Service
public class ModerationService {

    private final ModerationClient moderationClient;
    private final IdleItemRepository idleItemRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 文件上传目录，用于读取本地图片文件 */
    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 审核线程池：核心线程 2，最大线程 2，无界队列 */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
    );

    public ModerationService(ModerationClient moderationClient,
                             IdleItemRepository idleItemRepository,
                             HelpRequestRepository helpRequestRepository,
                             NotificationService notificationService) {
        this.moderationClient = moderationClient;
        this.idleItemRepository = idleItemRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.notificationService = notificationService;
    }

    /**
     * 应用关闭时优雅终止审核线程池。
     * 先尝试正常关闭（等待最多 30 秒），超时后强制中断。
     */
    @PreDestroy
    public void shutdown() {
        log.info("正在关闭审核线程池...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("审核线程池未在 30 秒内完成，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("审核线程池关闭被中断");
        }
    }

    // ==================== 对外调度接口 ====================

    /**
     * 将闲置物品提交到审核线程池。
     *
     * @param item 已保存的闲置物品实体（含 ID）
     */
    public void scheduleModeration(IdleItem item) {
        executor.submit(() -> moderateIdleItem(item));
    }

    /**
     * 将求助信息提交到审核线程池。
     *
     * @param hr 已保存的求助实体（含 ID）
     */
    public void scheduleModeration(HelpRequest hr) {
        executor.submit(() -> moderateHelpRequest(hr));
    }

    // ==================== 审核执行逻辑 ====================

    /**
     * 对闲置物品执行完整审核流程（图片 + 文本 → 汇总 → 分流处理）。
     *
     * @param item 闲置物品实体（可能为 detached 状态）
     */
    void moderateIdleItem(IdleItem item) {
        log.info("开始审核闲置物品: id={}, title={}", item.getId(), item.getTitle());
        Long itemId = item.getId();
        List<ModerationResult> results = new ArrayList<>();

        try {
            // 1. 审核每张图片
            List<String> imageUrls = parseImageUrls(item.getImages());
            for (String imageUrl : imageUrls) {
                try {
                    ModerationResult r = moderationClient.moderateImage(imageUrl, uploadDir);
                    results.add(r);
                    log.debug("图片审核完成: itemId={}, url={}, level={}", itemId, imageUrl, r.getLevel());
                } catch (Exception e) {
                    log.warn("图片审核失败（已跳过）: itemId={}, url={}", itemId, imageUrl, e);
                }
            }

            // 2. 审核文本
            try {
                ModerationResult textResult = moderationClient.moderateText(
                        item.getTitle(), item.getDescription());
                results.add(textResult);
                log.debug("文本审核完成: itemId={}, level={}", itemId, textResult.getLevel());
            } catch (Exception e) {
                log.warn("文本审核失败（已跳过）: itemId={}", itemId, e);
            }

            // 3. 汇总等级
            String finalLevel = aggregateLevel(results);
            String reason = buildReason(results, finalLevel);

            // 4. 重试一次：如果首次调用全部失败，再试一次
            if (results.isEmpty()) {
                log.warn("首次审核全部失败，执行重试: itemId={}", itemId);
                results = retryOnce(item);
                finalLevel = aggregateLevel(results);
                reason = buildReason(results, finalLevel);
            }

            applyResult(itemId, finalLevel, reason, "idle");

        } catch (Exception e) {
            log.error("审核过程异常，按 green 放行: itemId={}", itemId, e);
            // 兜底：异常时 green 放行，避免阻塞正常发布
            applyResult(itemId, ModerationStatus.GREEN, "", "idle");
        }
    }

    /**
     * 对求助信息执行完整审核流程（图片 + 文本 → 汇总 → 分流处理）。
     *
     * @param hr 求助实体（可能为 detached 状态）
     */
    void moderateHelpRequest(HelpRequest hr) {
        log.info("开始审核求助: id={}, title={}", hr.getId(), hr.getTitle());
        Long helpId = hr.getId();
        List<ModerationResult> results = new ArrayList<>();

        try {
            // 1. 审核每张图片
            List<String> imageUrls = parseImageUrls(hr.getImages());
            for (String imageUrl : imageUrls) {
                try {
                    ModerationResult r = moderationClient.moderateImage(imageUrl, uploadDir);
                    results.add(r);
                    log.debug("图片审核完成: helpId={}, url={}, level={}", helpId, imageUrl, r.getLevel());
                } catch (Exception e) {
                    log.warn("图片审核失败（已跳过）: helpId={}, url={}", helpId, imageUrl, e);
                }
            }

            // 2. 审核文本
            try {
                ModerationResult textResult = moderationClient.moderateText(
                        hr.getTitle(), hr.getDescription());
                results.add(textResult);
                log.debug("文本审核完成: helpId={}, level={}", helpId, textResult.getLevel());
            } catch (Exception e) {
                log.warn("文本审核失败（已跳过）: helpId={}", helpId, e);
            }

            // 3. 汇总等级
            String finalLevel = aggregateLevel(results);
            String reason = buildReason(results, finalLevel);

            // 4. 重试一次
            if (results.isEmpty()) {
                log.warn("首次审核全部失败，执行重试: helpId={}", helpId);
                results = retryOnce(hr);
                finalLevel = aggregateLevel(results);
                reason = buildReason(results, finalLevel);
            }

            applyResult(helpId, finalLevel, reason, "help");

        } catch (Exception e) {
            log.error("审核过程异常，按 green 放行: helpId={}", helpId, e);
            applyResult(helpId, ModerationStatus.GREEN, "", "help");
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 汇总多个审核结果，取最严重的等级。
     * <p>优先级：red > yellow > green</p>
     *
     * @param results 各维度审核结果列表
     * @return 最终审核等级
     */
    private String aggregateLevel(List<ModerationResult> results) {
        return results.stream()
                .map(ModerationResult::getLevel)
                .reduce(ModerationStatus.GREEN, (a, b) -> {
                    if (ModerationStatus.RED.equals(a) || ModerationStatus.RED.equals(b)) return ModerationStatus.RED;
                    if (ModerationStatus.YELLOW.equals(a) || ModerationStatus.YELLOW.equals(b)) return ModerationStatus.YELLOW;
                    return ModerationStatus.GREEN;
                });
    }

    /**
     * 构建汇总原因描述（仅 yellow/red 时拼接各维度原因）。
     *
     * @param results 各维度审核结果
     * @param level   最终等级
     * @return 原因描述字符串
     */
    private String buildReason(List<ModerationResult> results, String level) {
        if (ModerationStatus.GREEN.equals(level)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ModerationResult r : results) {
            if (r.getReason() != null && !r.getReason().isBlank()
                    && !ModerationStatus.GREEN.equals(r.getLevel())) {
                if (sb.length() > 0) sb.append("；");
                sb.append(r.getReason());
            }
        }
        return sb.toString();
    }

    /**
     * 重试一次审核（简易版，仅做文本审核）。
     */
    private List<ModerationResult> retryOnce(IdleItem item) {
        List<ModerationResult> results = new ArrayList<>();
        try {
            results.add(moderationClient.moderateText(item.getTitle(), item.getDescription()));
        } catch (Exception e) {
            log.error("重试审核失败: itemId={}", item.getId(), e);
        }
        return results;
    }

    /**
     * 重试一次审核（简易版，仅做文本审核）。
     */
    private List<ModerationResult> retryOnce(HelpRequest hr) {
        List<ModerationResult> results = new ArrayList<>();
        try {
            results.add(moderationClient.moderateText(hr.getTitle(), hr.getDescription()));
        } catch (Exception e) {
            log.error("重试审核失败: helpId={}", hr.getId(), e);
        }
        return results;
    }

    /**
     * 根据审核结果更新实体状态并通知用户。
     *
     * @param id     实体 ID
     * @param level  审核等级：green / yellow / red
     * @param reason 违规原因（green 时为空）
     * @param type   实体类型：idle / help
     */
    private void applyResult(Long id, String level, String reason, String type) {
        if ("idle".equals(type)) {
            idleItemRepository.findById(id).ifPresentOrElse(item -> {
                item.setModerationStatus(level);

                switch (level) {
                    case ModerationStatus.GREEN:
                        item.setStatus(BizStatus.ONLINE);
                        item.setDelistReason(null);
                        idleItemRepository.save(item);
                        log.info("闲置物品审核通过，自动上线: id={}", id);
                        // 通知用户审核通过
                        notificationService.create(item.getUserId(),
                                NotificationType.CONTENT_APPROVED,
                                "内容审核通过",
                                "您发布的「" + item.getTitle() + "」已通过审核，已自动上线展示",
                                item.getId());
                        break;
                    case ModerationStatus.YELLOW:
                        // 保持 pending_review，等待管理员复核
                        item.setDelistReason(reason);
                        idleItemRepository.save(item);
                        log.info("闲置物品待人工复核: id={}, reason={}", id, reason);
                        break;
                    case ModerationStatus.RED:
                        item.setStatus(BizStatus.OFFLINE);
                        item.setDelistReason(reason);
                        idleItemRepository.save(item);
                        log.info("闲置物品审核驳回: id={}, reason={}", id, reason);
                        // 通知用户内容被驳回
                        notificationService.create(item.getUserId(),
                                NotificationType.CONTENT_REJECTED,
                                "内容审核未通过",
                                "您发布的「" + item.getTitle() + "」未通过审核，原因：" + reason,
                                item.getId());
                        break;
                    default:
                        log.warn("未知审核等级: level={}, id={}", level, id);
                        break;
                }
            }, () -> log.error("审核后找不到闲置物品实体: id={}", id));
        } else {
            helpRequestRepository.findById(id).ifPresentOrElse(hr -> {
                hr.setModerationStatus(level);

                switch (level) {
                    case ModerationStatus.GREEN:
                        hr.setStatus(BizStatus.ONLINE);
                        hr.setDelistReason(null);
                        helpRequestRepository.save(hr);
                        log.info("求助审核通过，自动上线: id={}", id);
                        notificationService.create(hr.getUserId(),
                                NotificationType.CONTENT_APPROVED,
                                "内容审核通过",
                                "您发布的求助「" + hr.getTitle() + "」已通过审核，已自动上线展示",
                                hr.getId());
                        break;
                    case ModerationStatus.YELLOW:
                        hr.setDelistReason(reason);
                        helpRequestRepository.save(hr);
                        log.info("求助待人工复核: id={}, reason={}", id, reason);
                        break;
                    case ModerationStatus.RED:
                        hr.setStatus(BizStatus.OFFLINE);
                        hr.setDelistReason(reason);
                        helpRequestRepository.save(hr);
                        log.info("求助审核驳回: id={}, reason={}", id, reason);
                        notificationService.create(hr.getUserId(),
                                NotificationType.CONTENT_REJECTED,
                                "内容审核未通过",
                                "您发布的求助「" + hr.getTitle() + "」未通过审核，原因：" + reason,
                                hr.getId());
                        break;
                    default:
                        log.warn("未知审核等级: level={}, id={}", id, level);
                        break;
                }
            }, () -> log.error("审核后找不到求助实体: id={}", id));
        }
    }

    /**
     * 解析图片 JSON 数组字符串。
     * <p>格式：["url1", "url2"] 或 null</p>
     *
     * @param imagesJson JSON 数组字符串
     * @return 图片 URL 列表
     */
    private List<String> parseImageUrls(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析图片 JSON 失败: imagesJson={}", imagesJson, e);
            return List.of();
        }
    }
}
