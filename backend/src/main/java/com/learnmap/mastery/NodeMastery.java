package com.learnmap.mastery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 知识画像的一格：某个学习者在自己副本里某个节点上的 Mastery / Confidence */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "node_mastery")
public class NodeMastery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(nullable = false)
    private double mastery;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NodeMastery(Long learnerId, Long nodeId) {
        this.learnerId = learnerId;
        this.nodeId = nodeId;
    }
}
