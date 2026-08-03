package com.platform.poc;

import com.platform.ai.moderation.ModerationClient;
import com.platform.ai.moderation.ModerationResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Step 1 验证 — 文本审核切 deepseek 后是否正常（手写 ModerationClient 链路）。
 *
 * <p>验证结果（2026-08-03）：deepseekRestClient 注入成功，moderateText 走 deepseek
 * 返回合法 JSON 解析成功（无异常），链路通。@Disabled 保留作参考。</p>
 */
@SpringBootTest
@Disabled("Step 1 已验证通过，保留作参考")
class TextModerationPoc {

    private static final Logger log = LoggerFactory.getLogger(TextModerationPoc.class);

    @Autowired
    private ModerationClient moderationClient;

    @Test
    void moderateTextUsesDeepseek() {
        ModerationResult normal = moderationClient.moderateText("转让九成新篮球", "正常闲置物品，小区邻居自取");
        log.error("=== 文本审核[正常物品](deepseek): level={} reason={} ===",
                normal.getLevel(), normal.getReason());

        ModerationResult bad = moderationClient.moderateText("便宜卖几条烟", "中华玉溪都有");
        log.error("=== 文本审核[违禁烟](deepseek): level={} reason={} ===",
                bad.getLevel(), bad.getReason());
    }
}
