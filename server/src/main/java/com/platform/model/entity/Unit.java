package com.platform.model.entity;

import com.platform.model.entity.column.UnitsColumn;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 单元实体，对应 units 表。
 *
 * <p>楼栋下的二级空间划分，每个单元属于一个楼栋（building）。
 * 单元下包含多个房间（room）。</p>
 */
@Entity
@Table(name = UnitsColumn.TABLE_NAME)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit {

    /** 单元 ID（自增主键） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属楼栋 ID，外键 → buildings.id */
    @Column(name = UnitsColumn.COL_BUILDING_ID, nullable = false)
    private Long buildingId;

    /** 单元名称（如 "1单元"、"2单元"） */
    @Column(name = UnitsColumn.COL_NAME, nullable = false, length = 50)
    private String name;

    /** 创建时间 */
    @Column(name = UnitsColumn.COL_CREATED_AT, nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 关联楼栋实体（懒加载） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = UnitsColumn.COL_BUILDING_ID, insertable = false, updatable = false)
    private Building building;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
