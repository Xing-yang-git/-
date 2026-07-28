package com.platform.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * 数据导出请求体。
 * 前端勾选导出项目、日期范围后提交。
 */
@Data
public class ExportRequest {

    /**
     * 勾选的导出项目列表。
     * 可选值：residents / posts / borrows / removals / ratings
     */
    @NotEmpty(message = "请至少选择一项导出内容")
    private List<String> options;

    /** 筛选开始日期（yyyy-MM-dd），可选，不传则导出全部 */
    private String dateStart;

    /** 筛选结束日期（yyyy-MM-dd），可选，不传则导出全部 */
    private String dateEnd;

    /** 导出格式，固定 "xlsx" */
    private String format;
}
