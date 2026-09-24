package com.learnmap.diagnostic;

import com.learnmap.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "diagnostic_item")
public class DiagnosticItem extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(nullable = false)
    private int ordinal;

    /** 学习者副本里的节点 */
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private boolean answered;

    private Boolean correct;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
