package com.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 对话请求 DTO。
 */
@Data
public class AgentChatRequest {

    /** 用户消息（≤500 字符） */
    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "消息过长")
    private String message;
}
