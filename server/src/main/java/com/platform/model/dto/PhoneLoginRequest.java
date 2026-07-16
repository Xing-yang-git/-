package com.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class PhoneLoginRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;

    private Long tenantId;
}
