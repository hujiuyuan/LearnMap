package com.learnmap.practice;

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
@Table(name = "practice_session")
public class PracticeSession extends BaseEntity {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeMode mode;

    @Column(name = "target_node_id")
    private Long targetNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticePhase phase;

    @Column(nullable = false)
    private int step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
