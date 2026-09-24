package com.learnmap.retest;

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
@Table(name = "scheduled_retest")
public class ScheduledRetest extends BaseEntity {

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RetestReason reason;

    /** 学这个节点时用的学习方式（ActivityType 名），用于学习方式画像 */
    @Column(name = "source_mode", length = 16)
    private String sourceMode;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(nullable = false)
    private boolean done;

    private Boolean correct;

    @Column(name = "done_at")
    private LocalDateTime doneAt;
}
