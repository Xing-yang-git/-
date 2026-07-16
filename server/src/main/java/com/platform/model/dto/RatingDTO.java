package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDTO {
    private Long id;
    private String fromUserName;
    private Integer score;
    private LocalDateTime createdAt;
    private Double averageScore;
    private int totalRatings;
}
