package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemDTO {
    private UUID id;
    private String type;            // "idle" | "help"
    private String title;
    private String description;
    private String category;
    private BigDecimal price;       // idle only
    private String condition;       // idle only
    private String publisherName;
    private String publisherRoom;   // "3栋2单元1502号(业主)"
    private String displayStatus;   // "展示中"|"进行中"|"已完成"|"已下架"
    private String rawStatus;       // actual DB status value
    private Boolean isProxy;
    private Boolean isUrgent;       // help only
    private LocalDateTime createdAt;

    // Peer party (for in-progress / completed)
    private String peerName;
    private String peerRoom;
    private Double peerRating;

    // Time range (help only, or borrow period)
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;

    // Rating info (for completed items)
    private String publisherRatingStars;
    private Double publisherRatingScore;
    private String peerRatingStars;
    private Double peerRatingScore;

    // Violation info (for offline tab)
    private String violationType;
    private String violationReason;
    private String violatorName;
    private LocalDateTime violatedAt;

    // Building for filter
    private String building;
}
