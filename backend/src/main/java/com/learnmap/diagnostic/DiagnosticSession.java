package com.learnmap.diagnostic;

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
@Table(name = "diagnostic_session")
public class DiagnosticSession extends BaseEntity {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
