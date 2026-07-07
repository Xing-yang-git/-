package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingDTO {
    private UUID id;
    private String fromUserName;
    private Integer score;
    private String dimensionScores;
    private LocalDateTime createdAt;
    private Double averageScore;
    private int totalRatings;
}
