package com.learnmap.practice;

import com.learnmap.common.BaseEntity;
import com.learnmap.evidence.ErrorType;
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
@Table(name = "practice_item")
public class PracticeItem extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticePhase phase;

    @Column(nullable = false)
    private int step;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private boolean answered;

    private Boolean correct;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 24)
    private ErrorType errorType;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
