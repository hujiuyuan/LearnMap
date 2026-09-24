package com.learnmap.card;

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
@Table(name = "review_card")
public class ReviewCard extends BaseEntity {

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String front;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String back;

    @Column(nullable = false, length = 16)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardState state;

    @Column(nullable = false)
    private double stability;

    @Column(nullable = false)
    private double difficulty;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "last_review_at")
    private LocalDateTime lastReviewAt;
}
