package com.platform.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ContentOfflineRequest {
    private String targetType;          // "idle" | "help"
    private List<String> reasons;       // ["商业广告", "虚假信息"]
    private String customReason;        // 自定义原因文本
}
