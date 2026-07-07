package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {
    private long onlineIdleCount;
    private long onlineHelpCount;
    private long monthlyPublishes;
    private long monthlyCompletedBorrows;
    private double completionRate;
    private long monthlyActiveUsers;
    private long damageCount;
    private List<CategoryStat> categoryStats;
    private List<ItemStat> itemStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryStat {
        private String category;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemStat {
        private String label;
        private long value;
    }
}
