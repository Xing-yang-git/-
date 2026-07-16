package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "room_number", nullable = false, length = 10)
    private String roomNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false)
    private Unit unit;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
