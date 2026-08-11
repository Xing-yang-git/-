package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 运营看板统计 DTO — B 端 /dashboard 一次性返回全部展示数据。
 *
 * <p>结构对齐前端 DashboardView.vue 的数据需求：KPI 卡片（含较上月环比）、月度互助趋势
 * （周/月/季三段）、本月互助完成率、损坏三态统计、互助对象排行（全量，前端自行切 Top5 与弹窗筛选）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    /** KPI 卡片四项（key：idle/help/pub/mau，固定顺序） */
    private List<KpiStat> kpis;

    /** 月度互助趋势三段（周/月/季） */
    private Trends trends;

    /** 本月互助完成率 */
    private CompletionStat completion;

    /** 损坏三态统计（damageType 分布） */
    private DamageStat damage;

    /** 互助对象排行（全量，按互助次数降序） */
    private List<RankingItem> ranking;

    /** KPI 单项：value 为本期值，momChange 为较上月环比百分比（如 12.5 = ↑12.5%） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KpiStat {
        /** 指标 key：idle(在线闲置)/help(在线求助)/pub(本月发布)/mau(本月活跃) */
        private String key;
        /** 本期值 */
        private long value;
        /** 较上月环比（%，可为负） */
        private double momChange;
    }

    /** 趋势图三段数据容器 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Trends {
        /** 近 7 天（按天） */
        private TrendData week;
        /** 本月（按周分桶） */
        private TrendData month;
        /** 本季度（按月） */
        private TrendData quarter;
    }

    /** 趋势图单段数据：labels 与 publish/completed 数组一一对应 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendData {
        /** 分桶标签（周：M/d 与「今日」；月：第N周；季：N月） */
        private List<String> labels;
        /** 各桶发布数（闲置+求助，按创建时间） */
        private List<Long> publish;
        /** 各桶完成互助数（归还完成借用 + 完成帮助，按各自完成时间） */
        private List<Long> completed;
    }

    /** 本月互助完成率：completed 为已互助数、removed 为直接下架数、rate 为完成率百分比（1 位小数） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompletionStat {
        /** 已互助数（本月完成借用 + 完成帮助） */
        private long completed;
        /** 直接下架数（本月发布且状态 offline 的闲置+求助） */
        private long removed;
        /** 完成率（%）：completed / (completed + removed) * 100 */
        private double rate;
    }

    /** 损坏三态统计：按借用记录 damageType 分布（normal/severe/broken） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DamageStat {
        /** 正常耗损（damageType=normal） */
        private long normal;
        /** 非正常损坏（damageType=severe） */
        private long severe;
        /** 完全损坏（damageType=broken） */
        private long broken;
    }

    /** 互助对象排行单项：按住户聚合的互助总次数（闲置借入 + 技能接单合并） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RankingItem {
        /** 住户展示名（如「3栋2单元1502号(业主)」，由 UserFormatter.formatRoomWithType 生成） */
        private String name;
        /** 互助总次数（闲置借入 + 技能接单合并） */
        private long count;
    }
}
