package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidentDTO {
    private UUID id;
    private String name;
    private String room;       // "3栋2单元1502号"
    private String userType;   // "业主" | "租客"
    private String phone;      // masked: "138****8888"
}
