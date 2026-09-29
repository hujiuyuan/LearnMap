package com.learnmap.palace;

import com.learnmap.card.CardState;
import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "memory_palace")
public class MemoryPalace extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String place;

    @Column(name = "graph_id")
    private Long graphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PalaceStatus status = PalaceStatus.BUILDING;

    @Column(name = "forward_ok", nullable = false)
    private boolean forwardOk;

    @Column(name = "backward_ok", nullable = false)
    private boolean backwardOk;

    @Column(name = "random_ok", nullable = false)
    private int randomOk;

    /** 重走宫殿用 FSRS 排时间 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardState state = CardState.NEW;

    @Column(nullable = false)
    private double stability;

    @Column(nullable = false)
    private double difficulty;

    @Column(nullable = false)
    private int reps;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "last_walk_at")
    private LocalDateTime lastWalkAt;
}
