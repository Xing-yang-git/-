package com.platform.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class RatingRequest {
    // Backend field names
    private UUID borrowId;
    private UUID helpApplicationId;
    private Integer score;
    private String dimensionScores;

    // C端 miniprogram field names (aliases)
    private UUID targetId;        // maps to borrowId or helpApplicationId
    private String ratingType;    // "borrow" or "help"
    private Integer overallScore; // maps to score
    private String dimScores;     // C端: actual score numbers [4,5]
    private String dimLabels;     // C端: label names ["物品完好度","沟通态度"]
}
