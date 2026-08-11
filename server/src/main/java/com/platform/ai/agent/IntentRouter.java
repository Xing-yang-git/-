package com.platform.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 意图路由 — 解析模型最终回复中的 JSON 写操作意图，生成动作卡片。
 *
 * <p>写操作（发布闲置/求助/借入）不注册为可自动执行的工具（避免落库），
 * 而是由 System Prompt 指示模型返回 JSON：{"intent":"publish_xxx","params":{...}}，
 * 本组件解析后交给前端渲染确认卡片。</p>
 */
@Slf4j
@Component
public class IntentRouter {

    private final ObjectMapper objectMapper;

    /** 写操作 intent → 动作卡片按钮文案；goto_publish 为「发布指引」快捷跳转（前端渲染成蓝色"去发布 ›"链接） */
    private static final Map<String, String> ACTION_LABEL = Map.of(
            "publish_help", "帮您发起求助",
            "publish_idle", "帮您发布闲置",
            "publish_wanted", "帮您发布借入需求",
            "goto_publish", "去发布"
    );

    public IntentRouter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析回复文本中的 JSON 意图。
     *
     * @param reply 模型最终回复
     * @return 动作卡片；若文本非写操作 JSON 意图则返回 null（作为普通回答展示）
     */
    public AgentAction parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        String json = extractJson(reply);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String intent = node.path("intent").asText("");
            if (!ACTION_LABEL.containsKey(intent)) {
                return null;
            }
            Map<String, Object> params = objectMapper.convertValue(
                    node.path("params"), new TypeReference<Map<String, Object>>() {});
            return new AgentAction(intent, ACTION_LABEL.get(intent), params);
        } catch (Exception e) {
            log.debug("意图解析失败（按普通回答处理）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从回复中剔除 JSON 意图段，返回气泡展示的自然语言文本。
     *
     * <p>模型按指示返回写操作 JSON 时，原始 JSON 不应逐字播放给用户（气泡只展示
     * 自然语言 + 动作卡片）。若剔除后为空（模型只返回纯 JSON），由调用方替换为友好提示。</p>
     *
     * @param reply 模型原始回复
     * @return 剔除 JSON 段后的文本（可能为空）
     */
    public String stripJson(String reply) {
        if (reply == null) {
            return "";
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return (reply.substring(0, start) + reply.substring(end + 1)).trim();
        }
        return reply.trim();
    }

    /**
     * 从文本中提取 JSON 部分（兼容 markdown 代码块包裹）。
     *
     * @param text 模型返回的完整文本
     * @return 纯 JSON 字符串，或 null
     */
    private String extractJson(String text) {
        String trimmed = text.trim();
        // 去除 markdown 代码块 ```json ... ```
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return null;
    }

    /**
     * 兜底剥离回复中的 JSON 写操作意图段（供展示文本使用）。
     *
     * <p>与 {@link #stripJson} 不同：只移除包含 {@code "intent"} 字段的 JSON 对象块，
     * 用逐层括号匹配定位完整对象，不误伤正文中的花括号（如普通文字里的 {xx}）。
     * 即使意图解析失败（格式偏差/非法 intent），也能保证 JSON 不泄漏给用户。</p>
     *
     * @param text 模型原始回复
     * @return 剥离意图 JSON 后的文本（可能为空；调用方对空做兜底文案）
     */
    public String stripIntentJson(String text) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = matchBrace(text, start);
            if (end < 0) {
                break;
            }
            if (text.substring(start, end + 1).contains("\"intent\"")) {
                return (text.substring(0, start) + text.substring(end + 1)).trim();
            }
            start = text.indexOf('{', end + 1);
        }
        return text.trim();
    }

    /** 从 start（指向 '{'）逐层括号匹配，返回对应 '}' 的下标；不匹配返回 -1 */
    private int matchBrace(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
