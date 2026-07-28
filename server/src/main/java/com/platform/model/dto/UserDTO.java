package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String openid;
    private String username;
    private String name;
    private String phone;
    private String avatarUrl;
    private String authStatus;
    private String userType;
    private Long roomId;
    private Long tenantId;
    private String roomInfo;
    private String userRoom;
    private String tenantName;
    private List<String> docImages;
    private String rejectReason;
    private String bannedReason;
    private String auditorName;
    private LocalDateTime createdAt;
}
