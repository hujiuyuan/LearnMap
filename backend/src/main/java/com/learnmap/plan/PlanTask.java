package com.learnmap.plan;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan_task")
public class PlanTask extends BaseEntity {

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "block_no", nullable = false)
    private int blockNo;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 24)
    private TaskType taskType;

    @Column(name = "node_id")
    private Long nodeId;

    @Column(name = "card_count")
    private Integer cardCount;

    @Column(nullable = false)
    private int minutes;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "done_at")
    private LocalDateTime doneAt;
}
