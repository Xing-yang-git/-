package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "units")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", insertable = false, updatable = false)
    private Building building;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
