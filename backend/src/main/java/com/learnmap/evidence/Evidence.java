package com.learnmap.evidence;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 所有学习活动统一记录成证据，画像只读证据。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "evidence")
public class Evidence extends BaseEntity {

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    /** 学习者副本里的节点 */
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 16)
    private ActivityType activityType;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "solution_id")
    private Long solutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvidenceResult result;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private double difficulty;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", length = 24)
    private ErrorType errorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_confidence", length = 16)
    private SelfConfidence selfConfidence;
}
