package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private UUID id;
    private String openid;
    private String username;
    private String name;
    private String phone;
    private String avatarUrl;
    private String authStatus;
    private String userType;
    private UUID roomId;
    private String roomInfo;
    private String userRoom;
    private String tenantName;
    private List<String> docImages;
    private String rejectReason;
    private String bannedReason;
    private LocalDateTime createdAt;
}
