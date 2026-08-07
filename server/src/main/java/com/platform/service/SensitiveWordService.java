package com.platform.service;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.platform.common.BizException;
import com.platform.common.SensitiveWordStatus;
import com.platform.model.dto.SensitiveWordDTO;
import com.platform.model.entity.SensitiveWord;
import com.platform.repository.SensitiveWordRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词匹配服务 — 对话输入前置过滤的统一匹配入口（与「消息前置过滤器」的共享契约）。
 *
 * <p>契约方法（签名不可修改，供并行任务调用）：
 * <ul>
 *   <li>{@link #contains(String)} — 归一化匹配，命中任一启用词（或激活缩写）返回 true；</li>
 *   <li>{@link #replace(String)} — 把命中词替换为星号（每个命中词替换为 ***）。</li>
 * </ul>
 *
 * <p>匹配策略：
 * <ul>
 *   <li>启用词在应用启动时（{@code @PostConstruct}）加载进内存缓存（volatile + 不可变集合），
 *       增删改 API 操作后由服务层主动刷新，改动即时生效；</li>
 *   <li>文本与词库均先走归一化流水线（全角转半角 → 转小写 → 繁转简 → 去空格/标点/emoji 干扰），
 *       再在归一化后的文本上做包含匹配，可命中「ｆｕｃｋ」「FUCK」「f u c k」「傻 逼」等变体；</li>
 *   <li>常见拼音缩写（sb/md/cnm 等）经固定映射表归一为对应敏感词，归一化文本包含缩写本身即等同
 *       命中对应敏感词（仅当对应敏感词为启用状态时缩写才生效）；</li>
 *   <li>仅 {@link SensitiveWordStatus#ENABLED} 的词参与匹配，停用词不生效。</li>
 * </ul>
 */
@Slf4j
@Service
public class SensitiveWordService {

    private final SensitiveWordRepository sensitiveWordRepository;

    /** 启用词缓存：归一化后的敏感词集合（volatile 保证多线程可见性，刷新时整体替换） */
    private volatile Set<String> wordSet = Collections.emptySet();

    /** 激活缩写缓存：归一化文本包含缩写即等同命中对应敏感词 */
    private volatile Set<String> abbrSet = Collections.emptySet();

    /** 每个命中词替换为的星号掩码 */
    private static final String MASK_TEXT = "***";

    /**
     * 常见拼音缩写 → 对应敏感词（归一化词形）固定映射表。
     * <p>覆盖本项目对话场景的常用脏话缩写与变体：拼音首字母缩写（sb/md/cnm）、
     * 英文脏词拼写变体（fck/fk）、谐音字（草泥马/艹）。命中缩写等同命中对应敏感词，
     * 因此词库中启用「傻逼」后，文本里的「sb」同样会被拦截。</p>
     */
    private static final Map<String, String> ABBR_TO_WORD = Map.of(
            "sb", "傻逼",
            "md", "妈的",
            "nmd", "你妈的",
            "cnm", "操你妈",
            "wqnmlgb", "我去你妈的",
            "草泥马", "操你妈",
            "艹", "草",
            "fck", "fuck",
            "fk", "fuck"
    );

    /**
     * 构造器注入。
     *
     * @param sensitiveWordRepository 敏感词仓储
     */
    public SensitiveWordService(SensitiveWordRepository sensitiveWordRepository) {
        this.sensitiveWordRepository = sensitiveWordRepository;
    }

    // ==================== 契约方法（消息前置过滤器调用，签名不可改） ====================

    /**
     * 归一化匹配 — 命中任一启用词（或激活缩写）返回 true。
     *
     * @param text 待检测文本
     * @return true 表示命中敏感词，应拦截
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String word : wordSet) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        for (String abbr : abbrSet) {
            if (normalized.contains(abbr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把命中词替换为星号 — 每个命中词（或缩写）在原文中替换为「***」。
     *
     * <p>匹配基于归一化文本，替换基于原文：归一化时记录每个归一化字符对应的原文下标，
     * 命中区间映射回原文后整段替换，词间插入的干扰字符（空格/标点/emoji）一并被掩码。</p>
     *
     * @param text 待替换文本
     * @return 替换后的文本（无命中时原样返回）
     */
    public String replace(String text) {
        if (text == null || text.isEmpty() || wordSet.isEmpty()) {
            return text;
        }
        NormalizedText normalizedText = normalizeWithPositions(text);
        if (normalizedText.text.isEmpty()) {
            return text;
        }
        // 收集全部命中区间（原文半开区间 [start, end)）
        List<int[]> spans = new ArrayList<>();
        collectMatchSpans(normalizedText, wordSet, spans);
        collectMatchSpans(normalizedText, abbrSet, spans);
        if (spans.isEmpty()) {
            return text;
        }
        // 按起始下标升序排序，重叠区间合并（如「傻逼」与「你妈的」部分重叠时避免二次替换错位）
        spans.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
        List<int[]> merged = new ArrayList<>();
        for (int[] span : spans) {
            if (!merged.isEmpty() && span[0] <= merged.get(merged.size() - 1)[1]) {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], span[1]);
            } else {
                merged.add(new int[]{span[0], span[1]});
            }
        }
        // 从后往前替换为 ***（避免前序替换改变后续下标）
        StringBuilder result = new StringBuilder(text);
        for (int i = merged.size() - 1; i >= 0; i--) {
            int[] span = merged.get(i);
            result.replace(span[0], span[1], MASK_TEXT);
        }
        return result.toString();
    }

    // ==================== 管理端 CRUD（super_admin，增删改后刷新缓存） ====================

    /**
     * 敏感词分页列表（可按状态过滤）。
     *
     * @param status 状态过滤（ENABLED/DISABLED，空或 null 查全部）
     * @param page   页码（0 基）
     * @param size   每页条数
     * @return 分页结果
     */
    public Page<SensitiveWordDTO> list(String status, int page, int size) {
        SensitiveWordStatus statusEnum = parseStatus(status);
        Pageable pageable = PageRequest.of(page, size);
        Page<SensitiveWord> result = statusEnum == null
                ? sensitiveWordRepository.findAll(pageable)
                : sensitiveWordRepository.findByStatus(statusEnum, pageable);
        return result.map(this::toDTO);
    }

    /**
     * 新增敏感词（词重复返回业务错误）。
     *
     * @param req 请求体（word 必填、status 缺省 ENABLED）
     * @return 新建敏感词 DTO
     * @throws BizException 词为空或已存在时抛出
     */
    @Transactional
    public SensitiveWordDTO create(SensitiveWordDTO req) {
        String word = trimWord(req.getWord());
        if (sensitiveWordRepository.existsByWord(word)) {
            throw new BizException("敏感词已存在：" + word);
        }
        SensitiveWordStatus status = req.getStatus() != null ? req.getStatus() : SensitiveWordStatus.ENABLED;
        SensitiveWord entity = SensitiveWord.builder()
                .word(word)
                .status(status)
                .build();
        SensitiveWord saved = sensitiveWordRepository.save(entity);
        refreshCache();
        log.info("新增敏感词 id={} word={} status={}", saved.getId(), saved.getWord(), saved.getStatus());
        return toDTO(saved);
    }

    /**
     * 编辑敏感词（word/status）。
     *
     * @param id  敏感词 ID
     * @param req 请求体（word 必填、status 可空保持原值）
     * @return 更新后的敏感词 DTO
     * @throws BizException 敏感词不存在或改名后与其他词重复时抛出
     */
    @Transactional
    public SensitiveWordDTO update(Long id, SensitiveWordDTO req) {
        SensitiveWord entity = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new BizException("敏感词不存在"));
        String word = trimWord(req.getWord());
        if (!word.equals(entity.getWord()) && sensitiveWordRepository.existsByWord(word)) {
            throw new BizException("敏感词已存在：" + word);
        }
        entity.setWord(word);
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        SensitiveWord saved = sensitiveWordRepository.save(entity);
        refreshCache();
        log.info("更新敏感词 id={} word={} status={}", saved.getId(), saved.getWord(), saved.getStatus());
        return toDTO(saved);
    }

    /**
     * 删除敏感词。
     *
     * @param id 敏感词 ID
     * @throws BizException 敏感词不存在时抛出
     */
    @Transactional
    public void delete(Long id) {
        SensitiveWord entity = sensitiveWordRepository.findById(id)
                .orElseThrow(() -> new BizException("敏感词不存在"));
        sensitiveWordRepository.delete(entity);
        refreshCache();
        log.info("删除敏感词 id={} word={}", id, entity.getWord());
    }

    // ==================== 缓存加载与刷新 ====================

    /**
     * 加载全部启用词到内存缓存（应用启动时执行，增删改操作后由 CRUD 方法主动调用）。
     *
     * <p>词库与缩写均以归一化后的词形入缓存：缩写仅当其对应敏感词为启用状态时才激活，
     * 避免词库未收录时缩写误拦截（如未启用「fuck」则「fck」不生效）。</p>
     */
    @PostConstruct
    public void refreshCache() {
        List<SensitiveWord> words = sensitiveWordRepository
                .findByStatusOrderByUpdatedAtDesc(SensitiveWordStatus.ENABLED);
        Set<String> enabled = new HashSet<>();
        for (SensitiveWord word : words) {
            String normalized = normalize(word.getWord());
            if (!normalized.isEmpty()) {
                enabled.add(normalized);
            }
        }
        Set<String> activeAbbrs = new HashSet<>();
        for (Map.Entry<String, String> entry : ABBR_TO_WORD.entrySet()) {
            if (enabled.contains(entry.getValue())) {
                activeAbbrs.add(entry.getKey());
            }
        }
        wordSet = Collections.unmodifiableSet(enabled);
        abbrSet = Collections.unmodifiableSet(activeAbbrs);
        log.info("敏感词缓存刷新完成：启用词 {} 个，激活缩写 {} 个", enabled.size(), activeAbbrs.size());
    }

    // ==================== 归一化与匹配辅助 ====================

    /**
     * 归一化流水线：全角转半角 → 转小写 → 繁转简 → 去空格/标点/emoji 干扰。
     *
     * @param text 原始文本
     * @return 归一化文本（仅保留小写字母、数字与汉字）
     */
    static String normalize(String text) {
        return normalizeWithPositions(text).text;
    }

    /**
     * 归一化文本并记录每个归一化字符在原文中的下标。
     *
     * <p>归一化前三步（全角转半角/转小写/繁转简）均按字符位 1:1 处理（繁转简的少见
     * 多字词组转换除外），故去干扰后保留的字符下标与原文下标近似一致，足够支撑
     * {@link #replace(String)} 的原文区间映射。</p>
     *
     * @param text 原始文本
     * @return 归一化文本 + 原文下标映射
     */
    private static NormalizedText normalizeWithPositions(String text) {
        String halfWidth = toHalfWidth(text);
        String lower = halfWidth.toLowerCase(Locale.ROOT);
        String simple = ZhConverterUtil.toSimple(lower);
        StringBuilder kept = new StringBuilder(simple.length());
        List<Integer> positions = new ArrayList<>(simple.length());
        for (int i = 0; i < simple.length(); i++) {
            char c = simple.charAt(i);
            if (isRetainedChar(c)) {
                positions.add(i);
                kept.append(c);
            }
        }
        int[] posArray = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            posArray[i] = positions.get(i);
        }
        return new NormalizedText(kept.toString(), posArray);
    }

    /**
     * 收集归一化文本中全部命中区间的原文下标范围（半开区间 [start, end)）。
     *
     * @param normalized 归一化结果（含原文下标映射）
     * @param patterns   待匹配模式集合（启用词库或激活缩写）
     * @param spans      输出区间列表
     */
    private void collectMatchSpans(NormalizedText normalized, Set<String> patterns, List<int[]> spans) {
        String text = normalized.text;
        for (String pattern : patterns) {
            if (pattern.length() > text.length()) {
                continue;
            }
            int from = 0;
            while ((from = text.indexOf(pattern, from)) >= 0) {
                spans.add(new int[]{
                        normalized.positions[from],
                        normalized.positions[from + pattern.length() - 1] + 1});
                from += pattern.length();
            }
        }
    }

    /**
     * 全角字符转半角：全角空格(U+3000)→半角空格，全角 ASCII 区(FF01~FF5E)→半角(21~7E)。
     *
     * @param text 原始文本
     * @return 半角文本
     */
    static String toHalfWidth(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '　') {
                chars[i] = ' ';
            } else if (c >= '！' && c <= '～') {
                chars[i] = (char) (c - 0xFEE0);
            }
        }
        return new String(chars);
    }

    /**
     * 保留字符判定：小写字母、数字、汉字（CJK 统一表意区）。
     * 其余字符（空格、标点、emoji 等）视为干扰，在归一化时剔除。
     *
     * @param c 字符
     * @return true 表示保留
     */
    private static boolean isRetainedChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || (c >= '一' && c <= '鿿');
    }

    /**
     * 解析状态过滤参数。
     *
     * @param status 原始状态字符串（可空）
     * @return 状态枚举；空或 null 返回 null（查全部）
     * @throws BizException 非 ENABLED/DISABLED 取值时抛出
     */
    private SensitiveWordStatus parseStatus(String status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        try {
            return SensitiveWordStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BizException("未知状态：" + status);
        }
    }

    /**
     * 去除词首尾空白并校验非空。
     *
     * @param word 原始词
     * @return 去空白后的词
     * @throws BizException 词为空时抛出
     */
    private String trimWord(String word) {
        if (word == null) {
            throw new BizException("敏感词不能为空");
        }
        String trimmed = word.trim();
        if (trimmed.isEmpty()) {
            throw new BizException("敏感词不能为空");
        }
        return trimmed;
    }

    /**
     * 实体转 DTO。
     *
     * @param entity 实体
     * @return DTO
     */
    private SensitiveWordDTO toDTO(SensitiveWord entity) {
        return SensitiveWordDTO.builder()
                .id(entity.getId())
                .word(entity.getWord())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 归一化结果：归一化文本 + 每个归一化字符在原文中的下标。
     */
    private static final class NormalizedText {
        /** 归一化后的文本 */
        final String text;
        /** 下标映射：text 第 i 个字符对应原文中的下标 */
        final int[] positions;

        NormalizedText(String text, int[] positions) {
            this.text = text;
            this.positions = positions;
        }
    }
}
