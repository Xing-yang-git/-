package com.platform.model.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 导出日志 DTO，用于前端导出日志列表展示。
 */
@Data
@Builder
public class ExportLogDTO {

    /** 日志ID */
    private Long id;

    /** 操作人姓名 */
    private String adminName;

    /** 导出时间 */
    private LocalDateTime createdAt;

    /** 导出格式 */
    private String exportFormat;

    /** 勾选项目列表（JSON数组字符串），如 ["residents","posts","borrows"] */
    private String selectedOptions;

    /** 各 Sheet 记录数汇总描述，如 "住户:120 发布:85 互助:46" */
    private String countSummary;

    /** 生成的文件名 */
    private String fileName;
}
